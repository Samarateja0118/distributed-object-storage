# 9. A chunked object's contentHash is a composite digest

Date: 2026-07-30
Status: Accepted

## Context

In Phase 1 an object's `content_hash` is the SHA-256 of its bytes, and it is also
the name of the blob holding them. Those two facts were the same fact.

ADR 8 separates them: the bytes now live in many chunks, each named by its own
digest, and nothing in the system is named by the whole object's hash. Multipart
(ADR 12) makes it worse — parts arrive in parallel, so no single stream ever sees
the object's bytes in order. There is no longer a natural moment at which the
whole-object hash is known.

Three options. Re-stream every chunk at `complete` to compute the true hash, at
the cost of a full extra read of every object uploaded. Follow S3, which uses the
true hash for single-stream uploads and a composite for multipart, distinguished
by a suffix. Or use a composite uniformly.

## Decision

An object's `contentHash` is the SHA-256 of the ordered list of its chunk
digests, lowercase hex, via the same `com.dfs.common.hash.Sha256` helper.

This applies to every object written from Phase 2 onward, single-stream and
multipart alike.

## Consequences

**It is computable at `complete` without reading a byte of content**, and it is
deterministic, so a retried upload of the same content produces the same object
identity. Idempotence survives, which was the property ADR 3 was protecting.

**Chunk-level deduplication is unaffected.** Chunks are still named by the true
SHA-256 of their bytes, so identical chunks still collapse to one stored copy.
Only the object-level name changes.

Now the costs, which are user-visible and worth stating bluntly.

**`contentHash` is no longer the SHA-256 of the object's bytes.** A client that
hashes a file locally and compares it against the returned ETag will not match.
Phase 1 clients could do that; Phase 2 clients cannot. This is a breaking change
in the meaning of a field whose type and name do not change — the kind that fails
quietly, which is why it is recorded rather than left to be discovered.

**Chunk size becomes part of the contract.** The same bytes chunked at 4 MiB and
at 8 MiB produce different composite digests. Changing `dfs.blob.chunk-size`
changes the identity of every object written afterward, while objects written
before keep theirs. The property is not a tuning knob that can be turned freely.

**The column holds two meanings.** Objects migrated from Phase 1 keep their true
whole-object hashes; objects written after do not. Nothing in the row
distinguishes them beyond age. The alternative — rewriting historical digests to
the composite form — was rejected because it would silently invalidate any ETag a
client had already stored.

**One test changed sides.** `ObjectLifecycleE2EIT` asserted that an uploaded
object's `contentHash` equalled the SHA-256 of its content — the very identity
this record breaks. It now asserts the two differ, and looks for the file on disk
under the chunk's digest rather than the object's. Recorded because an assertion
that inverts is easy to mistake for one that was simply wrong, and because it is
the cheapest place a client-visible break of this kind would have been caught.

The S3-style split was rejected for a narrower reason: it puts two kinds of value
in one column and adds a branch to every comparison, and the discipline of one
rule was judged more valuable than matching S3's exact bytes here.
