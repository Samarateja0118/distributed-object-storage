package com.dfs.metadata.domain.model;

import com.dfs.common.hash.Sha256;
import com.dfs.metadata.domain.exception.InvalidPartException;
import com.dfs.metadata.domain.exception.MetadataValidationException;
import com.dfs.metadata.domain.model.MultipartAssembly.PartSelection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultipartAssemblyTest {

    private static final long MIN = 100;

    private static ContentHash digest(char c) {
        return new ContentHash(String.valueOf(c).repeat(64));
    }

    /** A part of {@code sizes.length} chunks, each with its own digest. */
    private static UploadPart part(int number, char firstDigest, long... sizes) {
        List<ObjectChunk> chunks = new ArrayList<>();
        for (int i = 0; i < sizes.length; i++) {
            chunks.add(new ObjectChunk(i, digest((char) (firstDigest + i)), sizes[i], "blob-node-" + i));
        }
        return new UploadPart(number, new ObjectChunks(chunks));
    }

    private static PartSelection select(UploadPart part) {
        return new PartSelection(part.partNumber(), part.etag());
    }

    @Test
    @DisplayName("concatenates the selected parts' chunks in order, renumbered from zero")
    void concatenatesParts() {
        UploadPart first = part(1, 'a', 60, 40);
        UploadPart second = part(2, 'c', 25);

        ObjectChunks assembled = MultipartAssembly.assemble(
                List.of(second, first), List.of(select(first), select(second)), MIN);

        assertThat(assembled.chunks()).extracting(ObjectChunk::index).containsExactly(0, 1, 2);
        assertThat(assembled.chunks()).extracting(chunk -> chunk.digest().value())
                .containsExactly(digest('a').value(), digest('b').value(), digest('c').value());
        // The recorded node travels with each chunk unchanged (ADR 11).
        assertThat(assembled.chunks()).extracting(ObjectChunk::nodeId)
                .containsExactly("blob-node-0", "blob-node-1", "blob-node-0");
        assertThat(assembled.totalSizeBytes()).isEqualTo(125);
        // The object is named by every chunk of every part, as a single upload is.
        assertThat(assembled.compositeHash().value()).isEqualTo(Sha256.composite(List.of(
                digest('a').value(), digest('b').value(), digest('c').value())));
    }

    @Test
    @DisplayName("a part's etag is the composite digest of its own chunks")
    void partEtagIsCompositeOfItsChunks() {
        UploadPart part = part(3, 'a', 10, 20);

        assertThat(part.etag().value())
                .isEqualTo(Sha256.composite(List.of(digest('a').value(), digest('b').value())));
        assertThat(part.sizeBytes()).isEqualTo(30);
    }

    @Test
    @DisplayName("parts may be skipped, and uploaded parts left out are simply not used")
    void allowsGapsAndUnusedParts() {
        UploadPart first = part(1, 'a', 100);
        UploadPart unused = part(2, 'b', 100);
        UploadPart fifth = part(5, 'c', 1);

        ObjectChunks assembled = MultipartAssembly.assemble(
                List.of(first, unused, fifth), List.of(select(first), select(fifth)), MIN);

        assertThat(assembled.chunks()).extracting(chunk -> chunk.digest().value())
                .containsExactly(digest('a').value(), digest('c').value());
    }

    @Test
    @DisplayName("only the last part may be smaller than the minimum")
    void enforcesMinimumPartSizeExceptForTheLast() {
        UploadPart small = part(1, 'a', 99);
        UploadPart last = part(2, 'b', 1);

        assertThatThrownBy(() -> MultipartAssembly.assemble(
                List.of(small, last), List.of(select(small), select(last)), MIN))
                .isInstanceOf(InvalidPartException.class)
                .hasMessageContaining("Part 1 is 99 bytes");

        // A single small part is the last part, so it is allowed.
        assertThat(MultipartAssembly.assemble(List.of(small), List.of(select(small)), MIN).totalSizeBytes())
                .isEqualTo(99);
    }

    @Test
    @DisplayName("rejects a part named with an etag other than the one it was uploaded with")
    void rejectsStaleEtag() {
        UploadPart part = part(1, 'a', 100);

        assertThatThrownBy(() -> MultipartAssembly.assemble(
                List.of(part), List.of(new PartSelection(1, digest('f'))), MIN))
                .isInstanceOf(InvalidPartException.class)
                .hasMessageContaining("re-uploaded");
    }

    @Test
    @DisplayName("rejects parts that were never uploaded")
    void rejectsMissingPart() {
        UploadPart part = part(1, 'a', 100);

        assertThatThrownBy(() -> MultipartAssembly.assemble(
                List.of(part), List.of(select(part), new PartSelection(2, digest('b'))), MIN))
                .isInstanceOf(InvalidPartException.class)
                .hasMessageContaining("Part 2 has not been uploaded");
    }

    @Test
    @DisplayName("rejects parts out of order or repeated")
    void rejectsUnorderedOrRepeatedParts() {
        UploadPart first = part(1, 'a', 100);
        UploadPart second = part(2, 'b', 100);

        assertThatThrownBy(() -> MultipartAssembly.assemble(
                List.of(first, second), List.of(select(second), select(first)), MIN))
                .isInstanceOf(InvalidPartException.class)
                .hasMessageContaining("ascending");
        assertThatThrownBy(() -> MultipartAssembly.assemble(
                List.of(first), List.of(select(first), select(first)), MIN))
                .isInstanceOf(InvalidPartException.class)
                .hasMessageContaining("ascending");
    }

    @Test
    @DisplayName("rejects completing with no parts")
    void rejectsEmptySelection() {
        assertThatThrownBy(() -> MultipartAssembly.assemble(List.of(), List.of(), MIN))
                .isInstanceOf(InvalidPartException.class)
                .hasMessageContaining("at least one part");
    }

    @Test
    @DisplayName("part numbers run from 1 to 10,000, and a part cannot be empty")
    void validatesParts() {
        assertThatThrownBy(() -> part(0, 'a', 1)).isInstanceOf(MetadataValidationException.class);
        assertThatThrownBy(() -> part(10_001, 'a', 1)).isInstanceOf(MetadataValidationException.class);
        assertThat(part(10_000, 'a', 1).partNumber()).isEqualTo(10_000);
        assertThatThrownBy(() -> new UploadPart(1, ObjectChunks.empty()))
                .isInstanceOf(MetadataValidationException.class)
                .hasMessageContaining("at least one byte");
    }
}
