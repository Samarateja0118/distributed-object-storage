package com.dfs.blob;

import com.dfs.common.hash.Sha256;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BlobApiIT extends AbstractBlobNodeIT {

    private static final String BLOBS = "/internal/v1/blobs";

    @Test
    void storesAndReadsBackContent() throws Exception {
        byte[] content = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        mockMvc.perform(put(BLOBS + "/" + digest).header("Authorization", bearer()).content(content))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.digest").value(digest))
                .andExpect(jsonPath("$.sizeBytes").value(content.length));

        mockMvc.perform(get(BLOBS + "/" + digest).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content))
                .andExpect(header().string("ETag", "\"" + digest + "\""));
    }

    @Test
    void storesContentAtShardedPath() throws Exception {
        byte[] content = "sharded".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        mockMvc.perform(put(BLOBS + "/" + digest).header("Authorization", bearer()).content(content))
                .andExpect(status().isCreated());

        assertThat(storedPathFor(digest)).exists();
        assertThat(Files.readAllBytes(storedPathFor(digest))).isEqualTo(content);
    }

    @Test
    void storesEmptyContent() throws Exception {
        byte[] content = new byte[0];
        String digest = Sha256.hexOfContent(content);

        mockMvc.perform(put(BLOBS + "/" + digest).header("Authorization", bearer()).content(content))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(0));

        mockMvc.perform(get(BLOBS + "/" + digest).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));
    }

    /**
     * Re-storing identical bytes must be indistinguishable from the first write.
     * Retries after a timeout depend on this.
     */
    @Test
    void treatsRepeatedStoreAsSuccess() throws Exception {
        byte[] content = "stored twice".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        mockMvc.perform(put(BLOBS + "/" + digest).header("Authorization", bearer()).content(content))
                .andExpect(status().isCreated());
        mockMvc.perform(put(BLOBS + "/" + digest).header("Authorization", bearer()).content(content))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sizeBytes").value(content.length));
    }

    /**
     * The guard that keeps the store honest: content filed under the wrong name
     * would be served to every later caller that asks for those bytes.
     */
    @Test
    void rejectsContentThatDoesNotMatchItsDigest() throws Exception {
        String digest = Sha256.hexOfContent("what was promised".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(put(BLOBS + "/" + digest)
                        .header("Authorization", bearer())
                        .content("what was sent".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));

        assertThat(storedPathFor(digest)).doesNotExist();
    }

    @Test
    void leavesNoStagingFileBehindAfterRejectedWrite() throws Exception {
        String digest = Sha256.hexOfContent("promised".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(put(BLOBS + "/" + digest)
                        .header("Authorization", bearer())
                        .content("sent".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest());

        assertThat(stagingFileCount()).isZero();
    }

    @Test
    void rejectsMalformedDigest() throws Exception {
        for (String malformed : new String[]{"tooshort", "ABCDEF", "z".repeat(64)}) {
            mockMvc.perform(put(BLOBS + "/" + malformed).header("Authorization", bearer()).content("x"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid request"));
        }
    }

    /**
     * The digest is a path segment, so a traversal attempt must be rejected as a
     * malformed name rather than resolved against the filesystem.
     */
    @Test
    void rejectsPathTraversalInDigest() throws Exception {
        mockMvc.perform(get(BLOBS + "/..%2f..%2fetc%2fpasswd").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsMissingBlobAsNotFound() throws Exception {
        String absent = Sha256.hexOfContent("never stored".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(get(BLOBS + "/" + absent).header("Authorization", bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Blob not found"));
    }

    /**
     * Asserts the headers only. MockMvc hands HEAD to the GET handler and keeps
     * whatever it writes, so the empty body a real container produces cannot be
     * observed here.
     */
    @Test
    void answersHeadWithSize() throws Exception {
        byte[] content = "head me".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        mockMvc.perform(put(BLOBS + "/" + digest).header("Authorization", bearer()).content(content))
                .andExpect(status().isCreated());

        mockMvc.perform(head(BLOBS + "/" + digest).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", content.length))
                .andExpect(header().string("ETag", "\"" + digest + "\""));
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        byte[] content = "unauthenticated".getBytes(StandardCharsets.UTF_8);
        String digest = Sha256.hexOfContent(content);

        mockMvc.perform(put(BLOBS + "/" + digest).content(content))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(BLOBS + "/" + digest))
                .andExpect(status().isUnauthorized());

        assertThat(storedPathFor(digest)).doesNotExist();
    }

    @Test
    void rejectsUnverifiableToken() throws Exception {
        mockMvc.perform(get(BLOBS + "/" + Sha256.hexOfContent("x".getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }
}
