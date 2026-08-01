# 8. An object is an ordered list of chunks, owned by metadata

Date: 2026-07-30
Status: Accepted

## Context

Phase 1 buffers each upload in memory, bounded by `dfs.upload.max-object-size`.
That bound is not incidental: ADR 3 names a blob by the SHA-256 of its content,
so the digest has to be known before the first byte is stored, and the only way
to know it is to have seen all the bytes. Removing the memory bound means
changing what gets named.

Multi-node distribution needs the same thing from the other direction. Placing
whole objects on nodes makes a single object no larger than a single disk, and
makes the largest objects the least balanced.

Two questions follow. Is a chunk a storage unit or just a transport frame? And if
it is a storage unit, where does an object's chunk list live — as a
content-addressed manifest blob, or as rows in the metadata service?

## Decision

An object is an ordered list of fixed-size chunks, 8 MiB by default via
`dfs.blob.chunk-size`. Each chunk is stored independently and named by the
SHA-256 of its own bytes.

The chunk list lives in the metadata service as `object_chunks` rows, keyed
`(object_id, chunk_index)`, carrying `chunk_digest`, `size_bytes`, and the node
holding it.

Every object is chunked, including one that fits in a single chunk. Objects
stored before Phase 2 are migrated to a single synthetic chunk row whose digest
is their existing `content_hash`.

## Consequences

**ADR 3 holds unchanged, one level down.** A chunk is content-addressed, hashed
on write, rejected on mismatch, published by atomic rename. The constraint that a
digest precede its bytes now applies per chunk rather than per object — and
because a chunk is bounded, it can be hashed as it fills and sent as soon as it
is full. That is precisely what removes the memory bound: the orchestrator holds
one chunk, not one object.

**Reclamation gets simpler, not harder.** GC refcounts chunks with a SQL join
against `object_chunks`. The manifest-as-blob alternative would have kept the
metadata schema nearly untouched and made manifests deduplicate themselves, but
it was rejected for a specific reason: placement is mutable and a content-addressed
manifest is immutable, so a separate mutable chunk-to-node index would have been
needed anyway. Two stores that must agree, where one suffices.

**Range reads become an offset lookup** rather than a full fetch and discard,
which is why they are in Phase 2 at all.

The costs are real.

**Metadata now grows with stored volume, not object count.** A 100 GB object is
roughly 12,800 rows. `complete` writes them in one batch, and `object_chunks`
becomes the largest table in the system by a wide margin.

**Small objects pay for uniformity.** A 1 KB file is a row, a join, and a chunk
fetch where Phase 1 had a single blob read. A size threshold would have avoided
this, at the price of two storage representations that GC, replication, download,
and every future phase would each have to handle. One path was judged worth more
than the saved join.

**Deduplication is narrower than it sounds.** Fixed-size chunking deduplicates
identical content only when it is identically aligned. Content-defined chunking
would dedupe across insertions and shifts; it was not adopted, because it makes
chunk boundaries depend on content rather than on part boundaries, which
conflicts with independently uploadable parts (ADR 12).
