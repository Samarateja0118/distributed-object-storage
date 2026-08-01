package com.dfs.upload.application.service;

import com.dfs.common.hash.Sha256;
import com.dfs.upload.application.port.in.ObjectTransferUseCase;
import com.dfs.upload.application.port.out.BlobStorePort;
import com.dfs.upload.application.port.out.MetadataRegistryPort;
import com.dfs.upload.domain.exception.ObjectTooLargeException;
import com.dfs.upload.domain.model.CallerToken;
import com.dfs.upload.domain.model.ObjectChunkRef;
import com.dfs.upload.domain.model.ObjectDownload;
import com.dfs.upload.domain.model.RegisteredObject;
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

    public ObjectTransferService(BlobStorePort blobs, MetadataRegistryPort metadata, ChunkingPolicy chunking) {
        this.blobs = blobs;
        this.metadata = metadata;
        this.chunking = chunking;
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
     */
    @Override
    public RegisteredObject upload(UploadCommand command) {
        List<ObjectChunkRef> chunks = new ArrayList<>();
        long totalBytes = 0;

        try (InputStream content = command.content()) {
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
                blobs.store(digest, chunk, command.token());
                chunks.add(new ObjectChunkRef(index++, digest, filled, chunking.nodeId()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed while reading the object's content", e);
        }

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
     * The reverse: metadata resolves the key to a chunk list, then the chunks are
     * fetched in order as the caller reads them.
     *
     * <p>Metadata is consulted first because it is what enforces tenancy. A blob
     * node cannot make that check — a digest has no owner — so asking one for
     * content before the metadata service has confirmed this tenant owns the key
     * would let a caller read any chunk whose digest they could guess or had once
     * seen.
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
                return new ByteArrayInputStream(blobs.fetch(chunks.get(next++).digest(), token));
            }
        });
    }
}
