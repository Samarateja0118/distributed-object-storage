package com.dfs.upload.application.service;

import com.dfs.common.hash.Sha256;
import com.dfs.upload.application.port.in.ObjectTransferUseCase;
import com.dfs.upload.application.port.out.BlobStorePort;
import com.dfs.upload.application.port.out.MetadataRegistryPort;
import com.dfs.upload.domain.exception.DownstreamUnavailableException;
import com.dfs.upload.domain.exception.InvalidPartException;
import com.dfs.upload.domain.exception.ObjectTooLargeException;
import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectChunkRef;
import com.dfs.upload.domain.model.ObjectDownload;
import com.dfs.upload.domain.model.RegisteredObject;
import com.dfs.upload.domain.model.UploadedPart;
import com.dfs.upload.domain.placement.RendezvousPlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Joins the two halves of an object: the chunks on blob nodes and the pointer in
 * the metadata service.
 */
@Service
public class ObjectTransferService implements ObjectTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(ObjectTransferService.class);

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final BlobStorePort blobs;
    private final MetadataRegistryPort metadata;
    private final ChunkingPolicy chunking;
    private final RendezvousPlacement placement;

    public ObjectTransferService(BlobStorePort blobs,
                                 MetadataRegistryPort metadata,
                                 ChunkingPolicy chunking,
                                 RendezvousPlacement placement) {
        this.blobs = blobs;
        this.metadata = metadata;
        this.chunking = chunking;
        this.placement = placement;
    }

    /**
     * Stores the content chunk by chunk, then registers the metadata.
     *
     * <p>The order is the important part, and it is not arbitrary. Neither write
     * can be rolled back once the other fails — there is no transaction spanning
     * two services — so the choice is only about which inconsistency to be left
     * with:
     *
     * <ul>
     *   <li><b>Metadata first:</b> a failed content write leaves an object that
     *       clients can see and list but never download. The system is lying
     *       about what it holds, and only a repair job can tell.
     *   <li><b>Content first:</b> a failed metadata write leaves chunks nothing
     *       refers to. No client can observe them; they cost disk until GC
     *       reclaims unreferenced chunks in Phase 3.
     * </ul>
     *
     * <p>Unreferenced bytes are the cheaper failure, so they are the one we
     * accept. Content addressing makes them cheaper still: a retry of the same
     * upload computes the same digests and lands on the same chunks rather than
     * accumulating a fresh set of orphans each time.
     *
     * <p>Only one chunk is in memory at a time, which is what ADR 8 buys: an
     * object's size is no longer bounded by the heap, and the configured maximum
     * becomes a policy rather than a survival limit.
     *
     * <p>Each chunk goes to the node its digest ranks first, or the next one
     * that answers, and the node it actually went to is what gets registered
     * (ADR 11) — see {@link #storeOnFirstAvailableNode}.
     */
    @Override
    public RegisteredObject upload(UploadCommand command) {
        StoredContent stored = storeContent(command.content(), command.token());
        List<ObjectChunkRef> chunks = stored.chunks();
        long totalBytes = stored.totalBytes();

        String contentHash = Sha256.composite(chunks.stream().map(ObjectChunkRef::digest).toList());
        log.debug("Stored {} chunk(s) totalling {} bytes for {}/{} as {}",
                chunks.size(), totalBytes, command.bucketName(), command.objectKey(), contentHash);

        RegisteredObject registered = metadata.register(new MetadataRegistryPort.RegisterObjectCommand(
                command.bucketName(),
                command.objectKey(),
                totalBytes,
                command.contentType() == null ? DEFAULT_CONTENT_TYPE : command.contentType(),
                contentHash,
                chunks,
                command.userMetadata() == null ? Map.of() : command.userMetadata()),
                command.token());

        log.info("Uploaded {}/{} as version {} ({} bytes, {} chunk(s), digest {})",
                command.bucketName(), command.objectKey(), registered.versionId(),
                registered.sizeBytes(), chunks.size(), contentHash);
        return registered;
    }

    /**
     * Stores one part's bytes chunk by chunk, then registers the part with its
     * session in the metadata service.
     *
     * <p>The same order as a whole upload, for the same reason (ADR 6): the
     * chunks are written before the session hears of them, so a failed
     * registration — an expired session, another tenant's upload id — leaves
     * only unreferenced bytes, never a part that claims content it does not
     * have. The session is not checked first: that would cost a round trip on
     * every part to fail faster on the rare request that is going to fail
     * anyway, and the registration is where the check has to happen regardless.
     *
     * <p>The part is chunked independently of every other part, so its last
     * chunk may be short and its boundaries become chunk boundaries in the
     * finished object (ADR 12). Each request is held to the same size ceiling as
     * a single upload; the finished object may be larger.
     */
    @Override
    public UploadedPart uploadPart(UploadPartCommand command) {
        StoredContent stored = storeContent(command.content(), command.token());
        if (stored.chunks().isEmpty()) {
            // The metadata service would refuse it too — a part exists only as
            // its chunks — but nothing was written, so there is no reason to ask.
            throw new InvalidPartException("A part must contain at least one byte");
        }

        String etag = Sha256.composite(stored.chunks().stream().map(ObjectChunkRef::digest).toList());
        UploadedPart part = metadata.registerPart(new MetadataRegistryPort.RegisterPartCommand(
                command.bucketName(),
                command.uploadId(),
                command.partNumber(),
                stored.totalBytes(),
                etag,
                stored.chunks()),
                command.token());

        log.info("Uploaded part {} of upload {} in {} ({} bytes, {} chunk(s), etag {})",
                part.partNumber(), command.uploadId(), command.bucketName(),
                part.sizeBytes(), stored.chunks().size(), part.etag());
        return part;
    }

    /**
     * Reads content to its end, cutting it into chunks of the configured size
     * and writing each to its placed node as it goes. One chunk is in memory at
     * a time (ADR 8).
     *
     * <p>The size limit is enforced against the bytes actually read, because a
     * Content-Length can be understated or omitted; so an oversized request has
     * stored the chunks that fit before it is refused. Nothing registers them,
     * which leaves them unreferenced bytes for GC — the failure ADR 6 accepts.
     */
    private StoredContent storeContent(InputStream input, CallerToken token) {
        List<ObjectChunkRef> chunks = new ArrayList<>();
        long totalBytes = 0;

        try (InputStream content = input) {
            byte[] buffer = new byte[chunking.chunkSizeBytes()];
            int index = 0;
            int filled;

            // readNBytes rather than read: a short read is ordinary on a socket,
            // and filling the buffer is what keeps chunk boundaries a function of
            // the configured size rather than of how the bytes happened to arrive.
            // Identical content has to chunk identically, or deduplication and the
            // composite digest both come apart.
            while ((filled = content.readNBytes(buffer, 0, buffer.length)) > 0) {
                totalBytes += filled;
                if (totalBytes > chunking.maxObjectSizeBytes()) {
                    throw new ObjectTooLargeException(chunking.maxObjectSizeBytes());
                }

                byte[] chunk = filled == buffer.length ? buffer.clone() : Arrays.copyOf(buffer, filled);
                String digest = Sha256.hexOfContent(chunk);
                String nodeId = storeOnFirstAvailableNode(digest, chunk, token);
                chunks.add(new ObjectChunkRef(index++, digest, filled, nodeId));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed while reading the content", e);
        }
        return new StoredContent(List.copyOf(chunks), totalBytes);
    }

    private record StoredContent(List<ObjectChunkRef> chunks, long totalBytes) {
    }

    /**
     * Writes one chunk to the most preferred node that answers, and returns the
     * node it was written to.
     *
     * <p>Only a node that <em>did not answer</em> is skipped. A node that answered
     * and refused — a rejected token, a digest mismatch, an internal error — fails
     * the upload as before: another node would refuse the same token, and a
     * corrupt chunk is no better on a different disk. Falling through on those
     * would hide a real error behind a slower success, or a slower failure.
     *
     * <p>A fallback write needs no repair afterwards. The chunk is recorded
     * against the node that took it, and reads follow the record rather than the
     * ranking, so a chunk written to its second choice is read exactly like one
     * written to its first.
     *
     * <p>Deduplication survives placement because the ranking is a function of
     * the digest alone: identical chunks prefer the same node, whichever object
     * they belong to. The exception is a fallback write, which can leave one copy
     * on each of two nodes. That costs disk, not correctness, since each object
     * records which copy it uses.
     */
    private String storeOnFirstAvailableNode(String digest, byte[] chunk, CallerToken token) {
        DownstreamUnavailableException lastFailure = null;
        for (String nodeId : placement.rank(digest)) {
            try {
                blobs.store(nodeId, digest, chunk, token);
                if (lastFailure != null) {
                    log.warn("Chunk {} stored on fallback node {}", digest, nodeId);
                }
                return nodeId;
            } catch (DownstreamUnavailableException unavailable) {
                log.warn("Blob node {} did not answer for chunk {}; trying the next node in its ranking",
                        nodeId, digest);
                lastFailure = unavailable;
            }
        }
        // The ranking holds every configured node and is never empty, so this is
        // reached only when none of them answered.
        throw lastFailure;
    }

    /**
     * The reverse: metadata resolves the key to a chunk list, then the chunks are
     * fetched in order as the caller reads them.
     *
     * <p>Metadata is consulted first because it is what enforces tenancy. A blob
     * node cannot make that check — a digest has no owner — so asking one for
     * content before the metadata service has confirmed this tenant owns the key
     * would let a caller read any chunk whose digest they could guess or had once
     * seen.
     *
     * <p>Each chunk is fetched from the node recorded for it, never from where
     * the placement ranking would put it today (ADR 11). With one copy per chunk
     * there is no other node to try, so a read whose node is down fails rather
     * than falling back.
     */
    @Override
    public ObjectDownload download(DownloadQuery query) {
        RegisteredObject object = metadata.lookup(query.bucketName(), query.objectKey(), query.token());
        List<ObjectChunkRef> chunks = metadata.fetchChunks(query.bucketName(), query.objectKey(), query.token());
        return new ObjectDownload(object, chunkStream(chunks, query.token()));
    }

    /**
     * Fetches each chunk only once the reader reaches it, so serving a download
     * holds one chunk rather than a whole object.
     */
    private InputStream chunkStream(List<ObjectChunkRef> chunks, CallerToken token) {
        return new SequenceInputStream(new Enumeration<>() {

            private int next;

            @Override
            public boolean hasMoreElements() {
                return next < chunks.size();
            }

            @Override
            public InputStream nextElement() {
                if (!hasMoreElements()) {
                    throw new NoSuchElementException();
                }
                ObjectChunkRef chunk = chunks.get(next++);
                return new ByteArrayInputStream(blobs.fetch(chunk.nodeId(), chunk.digest(), token));
            }
        });
    }
}
