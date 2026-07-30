# 7. The orchestrator forwards the caller's token and holds no service account

Date: 2026-07-30
Status: Accepted

## Context

The upload orchestrator calls the blob node and the metadata service on behalf of
a client. Both require authentication. The orchestrator therefore needs some
credential for those calls, and there are two candidate designs:

1. Give the orchestrator its own service account, trusted by both downstreams.
2. Replay the caller's own token.

## Decision

The orchestrator has no credential of its own. It forwards the caller's access
token to both services.

The token is read from the validated `Jwt` principal, not re-read from the
inbound `Authorization` header, so what is forwarded is exactly what this service
verified. It is modelled as a `CallerToken` value type rather than a bare string.

## Consequences

**A service account would erase the tenant.** Every downstream request would
carry the orchestrator's authority instead of the caller's, and the metadata
service would see one identity performing all writes for all tenants. The
`tenant_id` claim is the entire isolation boundary (ADR 2); a service account
replaces it with "the orchestrator" and the boundary disappears at the first hop.

**It would also become the most valuable credential in the system.** An account
trusted by every storage service, held by the internet-facing component, is
precisely the credential worth stealing.

**Forwarding keeps authorization where it is enforced.** The metadata service
applies the same tenancy rules to an orchestrated request as to a direct one,
because it cannot tell the difference — which is the point.

**Taking the token from the validated `Jwt` matters.** Forwarding the raw header
would relay a string this service never checked, making it a proxy for tokens it
had not verified.

### Limits

The orchestrator's authority is exactly the caller's, so it cannot perform
anything the caller could not. Any future operation needing more — a GC sweep, a
cross-tenant repair — needs its own component and its own justification rather
than a privilege escalation here.

Downstream token rejections are surfaced to the caller as 401 or 403 rather than
being masked as a gateway error, since a rejected token is the caller's to fix.
Everything else from a downstream is reported as 502: a dependency failing is not
the caller's mistake and should not be reported as one.

Token lifetime now spans the whole orchestration. A token that expires mid-request
fails the second call after the first succeeded — which the write ordering in
ADR 6 already accounts for.
