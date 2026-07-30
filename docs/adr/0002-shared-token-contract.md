# 2. One shared token contract in `dfs-common`

Date: 2026-07-29
Status: Accepted

Supersedes the `X-Tenant-Id` header used by the metadata service before this.

## Context

The metadata service originally took the tenant from an `X-Tenant-Id` request
header. That is not an authorization boundary at all — any caller could address
any tenant's data by editing a header.

Fixing it means every service must validate tokens, which raises a second
problem. The auth service mints tokens; the metadata service, blob node, and
orchestrator validate them. Four services must agree on the signing algorithm,
the key derivation, the issuer string, and the claim names. Configured
independently, they drift: a service that validates slightly differently either
rejects traffic the issuer considers valid or, worse, accepts a token the others
reject. Drift of this kind fails silently and at the security boundary.

## Decision

Tenancy comes from the validated token's `tenant_id` claim, never from a header,
path, or body.

Both halves of the contract live in `dfs-common`, with exactly one definition
each:

- **`dfs.security.jwt`** — issuer and signing secret, one property block shared
  by the minting service and every validating service. `AuthProperties.Jwt`
  retains only `accessTokenTtl`, which is genuinely auth-specific.
- **`DfsResourceServerConfiguration`** — the single `JwtDecoder` bean, pinning
  HS256 and validating the issuer. A valid signature alone is insufficient: it
  proves only that something holding the secret minted the token, not that our
  auth service did.
- **`DfsTokenClaims`** — the one reader for tenant and user claims.
- **`DfsTestTokens`** — published from `dfs-common` as a test-jar so each service
  signs tokens in its own tests exactly as auth does.

## Consequences

Adding a resource server is now a dependency and a security filter chain, not a
decoder configuration to get right.

`dfs-common` publishing a test-jar is unusual and worth stating plainly: test
fixtures are shared deliberately, because a copied signing setup in four test
suites would drift from the issuer the moment one changed, and the tests would
keep passing while production broke.

Because the resource-server starter is optional in `dfs-common`, the shared
decoder is conditional on it — services with no HTTP surface do not pay for it.

The signing secret is symmetric (HS256), so every validating service holds
material that can also mint tokens. That is acceptable while all services are
first-party and deployed together; moving to asymmetric keys, where only auth
holds the private key, is the natural hardening step for Phase 3.
