package com.dfs.metadata.infrastructure.scheduling;

import com.dfs.metadata.application.port.in.MultipartUploadUseCase;
import com.dfs.metadata.config.MetadataProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Deletes multipart sessions that were never completed or aborted (ADR 12).
 *
 * <p>Not optional: without it an abandoned upload's staged chunk rows stay
 * forever, and the chunks they name sit in a state GC has no rule for — neither
 * orphaned, since a session row points at them, nor referenced, since no object
 * does. Deleting the session turns them into ordinary orphans.
 *
 * <p>Each pass works in batches, one transaction per batch, until a batch comes
 * back short, so a large backlog never holds one long lock. Expired sessions are
 * already refused by every operation, so a slow sweep costs disk, never
 * correctness.
 *
 * <p>Scheduled by {@link com.dfs.metadata.config.MetadataSchedulingConfiguration}
 * rather than with {@code @Scheduled}, whose string attributes take only
 * milliseconds or ISO-8601 — not the {@code 15m} the rest of the configuration
 * is written in.
 */
@Component
public class MultipartExpirySweep {

    private static final Logger log = LoggerFactory.getLogger(MultipartExpirySweep.class);

    private final MultipartUploadUseCase uploads;
    private final int batchSize;

    public MultipartExpirySweep(MultipartUploadUseCase uploads, MetadataProperties properties) {
        this.uploads = uploads;
        this.batchSize = properties.multipart().sweepBatchSize();
    }

    public void sweep() {
        try {
            int deleted;
            do {
                deleted = uploads.expireStaleUploads();
            } while (deleted >= batchSize);
        } catch (RuntimeException e) {
            // Logged and left for the next run: a failed sweep loses nothing, and
            // an exception escaping here would only be logged by the scheduler
            // with less context.
            log.warn("Multipart expiry sweep failed; it will retry on the next run", e);
        }
    }
}
