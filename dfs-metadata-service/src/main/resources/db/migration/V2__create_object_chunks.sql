-- Phase 2: an object becomes an ordered list of chunks (ADR 8).
--
-- This table is load-bearing for durability, not just for lookup (ADR 11): the
-- bytes on disk are named by digest and record nothing about which object they
-- belong to, so losing these rows makes every chunk unreachable even though all
-- of them still exist. Back it up like the blobs themselves.

CREATE TABLE object_chunks (
    object_id    UUID        NOT NULL REFERENCES objects (id) ON DELETE CASCADE,
    chunk_index  INTEGER     NOT NULL,
    chunk_digest VARCHAR(64) NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    -- Recorded, never recomputed (ADR 11). A chunk written to a fallback node is
    -- indistinguishable from a normal one on read, because the read follows this
    -- column rather than the placement ranking.
    node_id      VARCHAR(64) NOT NULL,
    PRIMARY KEY (object_id, chunk_index),
    CONSTRAINT ck_object_chunks_index_non_negative CHECK (chunk_index >= 0),
    CONSTRAINT ck_object_chunks_size_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT ck_object_chunks_digest_lowercase_hex CHECK (chunk_digest ~ '^[a-f0-9]{64}$')
);

-- ON DELETE CASCADE above is for GC in Phase 3: when it eventually purges an
-- object row, leaving its chunk rows behind would be precisely the dangling
-- reference GC exists to remove.

-- Serves the read path: fetching an object means reading its chunks in order.
-- The primary key already orders by (object_id, chunk_index), so no second
-- index is needed for it.

-- Serves chunk refcounting in Phase 3: "how many objects still reference this
-- digest" is the question GC asks before reclaiming any bytes.
CREATE INDEX ix_object_chunks_digest ON object_chunks (chunk_digest);

-- Every object written before Phase 2 is one whole blob, which is exactly a
-- single-chunk object whose chunk digest is the object's existing content_hash.
-- That value is also the name the bytes are filed under on disk, so migrated
-- objects download through the new chunked path with no special case, and the
-- ETags clients already hold stay valid.
--
-- These rows keep a true whole-object hash in objects.content_hash while every
-- object written from here on carries a composite (ADR 9). Nothing in the row
-- distinguishes the two beyond age; rewriting the old ones was rejected because
-- it would silently invalidate an ETag a client had stored.
INSERT INTO object_chunks (object_id, chunk_index, chunk_digest, size_bytes, node_id)
SELECT id, 0, content_hash, size_bytes, 'blob-node-0'
FROM objects
WHERE size_bytes > 0;

-- Zero-byte objects are deliberately excluded: no bytes means no chunks. They
-- need no row to stay correct, because SHA-256 of an empty chunk list is the
-- same value Phase 1 recorded as their content_hash — the one case where the old
-- and new meanings of the column coincide.

-- 'blob-node-0' is the single node Phase 1 ran, so every pre-Phase-2 chunk is on
-- it by definition. ADR 11's configured node set must keep that id when it
-- arrives, or these rows name a node that does not exist.
