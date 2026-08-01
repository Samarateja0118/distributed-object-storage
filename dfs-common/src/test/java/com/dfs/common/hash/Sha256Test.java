package com.dfs.common.hash;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The composite digest is an object's identity on the wire, so these assertions
 * are against fixed literals rather than against a second implementation of the
 * same formula — a test that recomputes the value it is checking would agree
 * with the code whether or not either is right, and would let the definition
 * drift silently.
 */
class Sha256Test {

    /** SHA-256 of no input. */
    private static final String EMPTY = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    /** SHA-256 of the single byte 'a', used below as a stand-in chunk digest. */
    private static final String DIGEST_A = "ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb";

    /** SHA-256 of the single byte 'b'. */
    private static final String DIGEST_B = "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d";

    @Test
    void hashesTheDecodedDigestBytesRatherThanTheirHexText() {
        // SHA-256 of DIGEST_A's 32 bytes, not of its 64 hex characters.
        assertThat(Sha256.composite(List.of(DIGEST_A)))
                .isEqualTo("bf5d3affb73efd2ec6c36ad3112dd933efed63c4e1cbffcfa88e2759c144f2d8");
    }

    @Test
    void concatenatesInListOrder() {
        assertThat(Sha256.composite(List.of(DIGEST_A, DIGEST_B)))
                .isEqualTo("e5a01fee14e0ed5c48714f22180f25ad8365b53f9779f79dc4a3d7e93963f94a");
    }

    @Test
    void ordersMatter() {
        assertThat(Sha256.composite(List.of(DIGEST_A, DIGEST_B)))
                .isNotEqualTo(Sha256.composite(List.of(DIGEST_B, DIGEST_A)));
    }

    /** A zero-byte object has no chunks, and lands on SHA-256 of no input. */
    @Test
    void hashesAnEmptyChunkListToTheEmptyDigest() {
        assertThat(Sha256.composite(List.of())).isEqualTo(EMPTY);
        assertThat(Sha256.hexOfContent(new byte[0])).isEqualTo(EMPTY);
    }

    /**
     * The property ADR 9 warns clients about: for anything with content, the
     * object's name is not the hash of its bytes.
     */
    @Test
    void doesNotMatchTheHashOfTheContentForASingleChunkObject() {
        byte[] content = "a".getBytes();
        assertThat(Sha256.hexOfContent(content)).isEqualTo(DIGEST_A);
        assertThat(Sha256.composite(List.of(DIGEST_A))).isNotEqualTo(DIGEST_A);
    }

    @Test
    void rejectsSomethingThatIsNotADigest() {
        assertThatThrownBy(() -> Sha256.composite(List.of("not-a-digest")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Sha256.composite(List.of(DIGEST_A.toUpperCase())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
