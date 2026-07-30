package com.dfs.upload.domain.model;

/**
 * The caller's own access token, carried through the orchestration so it can be
 * replayed to the blob node and the metadata service.
 *
 * <p>The orchestrator deliberately has no credential of its own. If it held a
 * service account, every request it made downstream would carry that account's
 * authority rather than the caller's, and the metadata service could no longer
 * tell one tenant's writes from another's — it would see only "the
 * orchestrator". Forwarding the caller's token keeps the tenant claim, and
 * therefore the isolation boundary, intact end to end.
 *
 * <p>Modelled as a type rather than a bare {@code String} so it cannot be
 * confused with the many other strings in these signatures, and so this
 * reasoning has somewhere to live.
 */
public record CallerToken(String value) {

    public CallerToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Caller token must not be blank");
        }
    }

    /** The value as it belongs in an {@code Authorization} header. */
    public String asBearerHeader() {
        return "Bearer " + value;
    }
}
