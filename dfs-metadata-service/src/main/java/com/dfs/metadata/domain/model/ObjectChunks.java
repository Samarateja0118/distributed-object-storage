package com.dfs.metadata.domain.model;

import com.dfs.common.hash.Sha256;
import com.dfs.metadata.domain.exception.MetadataValidationException;

import java.util.List;

/**
 * The ordered chunk list making up one object's content (ADR 8), and the rules
 * that make it a coherent object rather than a bag of digests.
 *
 * <p>This is where the composite digest is derived (ADR 9). The metadata service
 * recomputes it rather than trusting the value the caller sent: the caller
 * computed the same thing from the same list moments earlier, so agreeing costs
 * one hash of a few hundred bytes, while disagreeing silently would file an
 * object under a name that does not describe its content.
 */
public record ObjectChunks(List<ObjectChunk> chunks) {

    public ObjectChunks {
        if (chunks == null) {
            throw new MetadataValidationException("Chunk list is required");
        }
        chunks = List.copyOf(chunks);

        // Contiguity from zero is what makes the list an object. A gap would mean
        // a hole in the content that nothing else in the system would detect —
        // the download would simply be short, and the composite digest would be
        // perfectly consistent with the truncated list.
        for (int i = 0; i < chunks.size(); i++) {
            if (chunks.get(i).index() != i) {
                throw new MetadataValidationException(
                        "Chunk indexes must run from 0 without gaps; found %d at position %d"
                                .formatted(chunks.get(i).index(), i));
            }
        }
    }

    /** A zero-byte object: no bytes, so no chunks. */
    public static ObjectChunks empty() {
        return new ObjectChunks(List.of());
    }

    public long totalSizeBytes() {
        return chunks.stream().mapToLong(ObjectChunk::sizeBytes).sum();
    }

    public ContentHash compositeHash() {
        return new ContentHash(Sha256.composite(chunks.stream().map(chunk -> chunk.digest().value()).toList()));
    }

    /**
     * Checks the list against what the caller said it added up to. Both halves
     * matter: the digest catches wrong or reordered chunks, the size catches a
     * declared length that does not match the bytes actually placed.
     */
    public void requireConsistentWith(ContentHash declaredHash, long declaredSizeBytes) {
        ContentHash actual = compositeHash();
        if (!actual.equals(declaredHash)) {
            throw new MetadataValidationException(
                    "Content hash %s does not match the composite digest of the chunk list, %s"
                            .formatted(declaredHash.value(), actual.value()));
        }
        if (totalSizeBytes() != declaredSizeBytes) {
            throw new MetadataValidationException(
                    "Declared size %d does not match the chunk sizes, which total %d"
                            .formatted(declaredSizeBytes, totalSizeBytes()));
        }
    }
}
