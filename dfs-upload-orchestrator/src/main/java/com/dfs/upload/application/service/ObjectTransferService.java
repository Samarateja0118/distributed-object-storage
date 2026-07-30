package com.dfs.upload.application.service;

import com.dfs.common.hash.Sha256;
import com.dfs.upload.application.port.in.ObjectTransferUseCase;
import com.dfs.upload.application.port.out.BlobStorePort;
import com.dfs.upload.application.port.out.MetadataRegistryPort;
import com.dfs.upload.domain.model.ObjectDownload;
import com.dfs.upload.domain.model.RegisteredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Joins the two halves of an object: the bytes on a blob node and the pointer
 * in the metadata service.
 */
@Service
public class ObjectTransferService implements ObjectTransferUseCase {

    private static final Logger log = LoggerFactory.getLogger(ObjectTransferService.class);

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final BlobStorePort blobs;
    private final MetadataRegistryPort metadata;

    public ObjectTransferService(BlobStorePort blobs, MetadataRegistryPort metadata) {
        this.blobs = blobs;
        this.metadata = metadata;
    }

    /**
     * Stores the bytes first and registers the metadata second.
     *
     * <p>The order is the important part, and it is not arbitrary. Neither write
     * can be rolled back once the other fails — there is no transaction spanning
     * two services — so the choice is only about which inconsistency to be left
     * with:
     *
     * <ul>
     *   <li><b>Metadata first:</b> a failed blob write leaves an object that
     *       clients can see and list but never download. The system is lying
     *       about what it holds, and only a repair job can tell.
     *   <li><b>Blob first:</b> a failed metadata write leaves bytes nothing
     *       refers to. No client can observe them; they cost disk until GC
     *       reclaims unreferenced blobs in Phase 3.
     * </ul>
     *
     * <p>Unreferenced bytes are the cheaper failure, so they are the one we
     * accept. Content addressing makes them cheaper still: a retry of the same
     * upload computes the same digest and lands on the same blob rather than
     * accumulating a new orphan each time.
     */
    @Override
    public RegisteredObject upload(UploadCommand command) {
        String digest = Sha256.hexOfContent(command.content());

        blobs.store(digest, command.content(), command.token());
        log.debug("Stored blob {} ({} bytes) for {}/{}",
                digest, command.content().length, command.bucketName(), command.objectKey());

        RegisteredObject registered = metadata.register(new MetadataRegistryPort.RegisterObjectCommand(
                command.bucketName(),
                command.objectKey(),
                command.content().length,
                command.contentType() == null ? DEFAULT_CONTENT_TYPE : command.contentType(),
                digest,
                command.userMetadata() == null ? Map.of() : command.userMetadata()),
                command.token());

        log.info("Uploaded {}/{} as version {} ({} bytes, digest {})",
                command.bucketName(), command.objectKey(), registered.versionId(),
                registered.sizeBytes(), digest);
        return registered;
    }

    /**
     * The reverse: metadata resolves the key to a digest, then the blob node
     * serves the bytes filed under it.
     *
     * <p>Metadata is consulted first because it is what enforces tenancy. The
     * blob node cannot make that check — a digest has no owner — so asking it
     * for content before the metadata service has confirmed this tenant owns the
     * key would let a caller read any blob whose digest they could guess or had
     * once seen.
     */
    @Override
    public ObjectDownload download(DownloadQuery query) {
        RegisteredObject object = metadata.lookup(query.bucketName(), query.objectKey(), query.token());
        byte[] content = blobs.fetch(object.contentHash(), query.token());
        return new ObjectDownload(object, content);
    }
}
