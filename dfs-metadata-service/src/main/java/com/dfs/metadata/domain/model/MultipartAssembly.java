package com.dfs.metadata.domain.model;

import com.dfs.metadata.domain.exception.InvalidPartException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the parts a client names on completion into one object's chunk list.
 *
 * <p>The rules are S3's, because that is the protocol clients expect (ADR 12):
 *
 * <ul>
 *   <li>at least one part, in strictly ascending part-number order — gaps are
 *       allowed, so a client may leave out parts it no longer wants;
 *   <li>each named part must have been uploaded, with the etag the client gives,
 *       so a part re-uploaded after the client last looked cannot slip into the
 *       object unnoticed;
 *   <li>every part but the last must be at least the minimum part size.
 * </ul>
 *
 * <p>The object's chunk list is the parts' chunk lists end to end, renumbered
 * from zero. Its composite digest therefore covers every chunk of every part in
 * order, by the same rule as a single-request upload (ADR 9); the cost, recorded
 * in ADR 12, is that the client's choice of part sizes is part of the object's
 * identity.
 */
public final class MultipartAssembly {

    private MultipartAssembly() {
    }

    public record PartSelection(int partNumber, ContentHash etag) {
    }

    public static ObjectChunks assemble(
            List<UploadPart> uploaded, List<PartSelection> selected, long minPartSizeBytes) {
        if (selected == null || selected.isEmpty()) {
            throw new InvalidPartException("Completing an upload requires at least one part");
        }
        if (selected.size() > UploadPart.MAX_PART_NUMBER) {
            throw new InvalidPartException("An upload cannot have more than %d parts"
                    .formatted(UploadPart.MAX_PART_NUMBER));
        }

        Map<Integer, UploadPart> byNumber = uploaded.stream()
                .collect(Collectors.toMap(UploadPart::partNumber, Function.identity()));

        List<ObjectChunk> chunks = new ArrayList<>();
        int previousNumber = 0;
        for (int i = 0; i < selected.size(); i++) {
            PartSelection selection = selected.get(i);
            int number = selection.partNumber();
            UploadPart.requireValidPartNumber(number);
            if (number <= previousNumber) {
                throw new InvalidPartException(
                        "Parts must be listed in ascending order of part number without repeats; %d follows %d"
                                .formatted(number, previousNumber));
            }
            previousNumber = number;

            UploadPart part = byNumber.get(number);
            if (part == null) {
                throw new InvalidPartException("Part %d has not been uploaded".formatted(number));
            }
            if (!part.etag().equals(selection.etag())) {
                throw new InvalidPartException(
                        "Part %d has etag %s, not %s; it may have been re-uploaded"
                                .formatted(number, part.etag().value(), selection.etag().value()));
            }
            boolean last = i == selected.size() - 1;
            if (!last && part.sizeBytes() < minPartSizeBytes) {
                throw new InvalidPartException(
                        "Part %d is %d bytes; every part but the last must be at least %d"
                                .formatted(number, part.sizeBytes(), minPartSizeBytes));
            }

            for (ObjectChunk chunk : part.chunks().chunks()) {
                chunks.add(new ObjectChunk(chunks.size(), chunk.digest(), chunk.sizeBytes(), chunk.nodeId()));
            }
        }
        return new ObjectChunks(chunks);
    }
}
