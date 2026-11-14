# 12. S3-style multipart upload with server-side sessions

Date: 2026-07-30
Status: Accepted

## Context

ADR 8 makes streaming a whole object possible, but a single request is still a
single point of failure: a connection dropped at 90% costs the whole upload. For
the object sizes chunking exists to support, that is the common case rather than
the unlucky one.

Clients also already know a shape for this. S3's multipart protocol is what
tooling and SDKs expect, and matching it costs little beyond the state it
requires.

## Decision

Uploads may be performed as `initiate` / `upload-part` / `complete` / `abort`.
Session state lives in the metadata service as `multipart_uploads` and
`multipart_upload_chunks`; `complete` promotes the staged chunks into
`object_chunks` in one transaction.

Each part is chunked independently. Minimum part size is 5 MiB except for the
last; the part limit is 10,000. Sessions may be aborted explicitly, and a sweep
expires those that are never completed or aborted.

## Consequences

**Retry granularity becomes a part rather than an object**, which is the entire
point. A failed part is re-uploaded against the same session; nothing else is
re-sent.

**Blob-first ordering from ADR 6 is preserved and, if anything, clearer.** Chunks
are written before any object exists. An upload abandoned midway leaves chunks
that nothing references — the same state a deleted object leaves behind,
reclaimed by the same GC, exactly as ADR 4 anticipated.

**Chunking each part independently means part boundaries create chunk
boundaries.** A part's final chunk may be short, so the same file uploaded with
different part sizes produces different chunks and — by ADR 9 — a different
`contentHash`. Client-chosen part sizes therefore leak into object identity. The
alternative, requiring part sizes to be exact multiples of the chunk size, was
rejected because it pushes an internal storage parameter into the public API.

**The expiry sweep is not optional.** Without it, abandoned sessions accumulate
staged rows forever, and the chunks they reference sit in a state GC has no rule
for: not orphaned, because a session row still points at them, and not
referenced, because no object does. The sweep is what converts an abandoned
upload into an ordinary orphan that existing mechanisms already reclaim.

**Sessions are tenant-scoped and an `uploadId` is not an authorization.**
Possession of an id must not grant the ability to add parts to it or complete it;
the tenant still comes from the validated token, per ADR 2. An upload id is a
name, not a capability — the distinction is easy to lose when the id is an opaque
random string that looks like a secret.

The cost is a second lifecycle to manage — sessions that can be open, completed,
aborted, or expired — in a service that until now stored only durable facts.

**A staged chunk is a reference.** Until a session ends, its chunks are named by
`multipart_upload_chunks` and by nothing else. GC must count those rows alongside
`object_chunks` before reclaiming bytes, or it would delete a part from under an
upload about to complete.

**An empty part is refused.** A part exists only as its chunk rows, so a part
with no bytes could not be recorded; S3 tolerates a zero-byte last part, and this
does not.

**`max-object-size` now caps a request, not an object.** Each part is held to it,
as a single upload is, but a completed object may exceed it by any multiple —
which is what multipart is for. Before this, the property read as an object
limit.
