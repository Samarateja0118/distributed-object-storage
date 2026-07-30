# 4. The blob node has no DELETE endpoint

Date: 2026-07-30
Status: Accepted

## Context

The blob node exposes PUT, GET, and HEAD. A storage service without a delete
looks like an oversight, so the omission is worth recording as a decision.

Two facts make delete harder than it appears:

1. Blobs are content-addressed and therefore **shared across tenants** (ADR 3).
   The same bytes uploaded by two tenants are one file, referenced by two
   independent metadata records.
2. The blob node **cannot see references**. It knows digests and bytes; the
   metadata service knows which objects point at which digest, and there may be
   many across tenants and versions.

## Decision

No delete endpoint in Phase 1. Reclamation is deferred to a garbage collector
with refcounting, in Phase 3.

## Consequences

The obvious naive delete is unsafe, and it is worth being explicit about why: a
`DELETE /internal/v1/blobs/{digest}` has no owner to authorize against. Tenant A
deleting their object would remove bytes tenant B's object still points at, and
tenant B's next download would fail with no trace of the cause. Authorizing the
call does not help, because there is no correct answer to "may this caller delete
this digest" — the caller may legitimately own one of several references.

Deleting an object therefore removes the metadata record only. The bytes remain
until GC establishes that nothing references them.

This also makes the write ordering in ADR 6 cheap: an orphaned blob is not a
special failure state, it is the same state a deleted object leaves behind, and
one mechanism reclaims both.

The cost is that storage grows monotonically until Phase 3. For a single-node
Phase 1 this is acceptable; it becomes a real constraint at scale, which is why
GC is scheduled before the scale phase rather than after.
