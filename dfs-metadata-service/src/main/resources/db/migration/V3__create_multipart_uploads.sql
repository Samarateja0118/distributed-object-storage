-- Phase 2: S3-style multipart upload sessions (ADR 12).
--
-- A session is the one thing this service stores that is not a durable fact: it
-- is open until it is completed, aborted, or expires, and then its rows are
-- deleted. Completion promotes the staged chunks below into object_chunks in the
-- same transaction that writes the object, so an object never exists without its
-- chunk list and a session never outlives the object it became.

CREATE TABLE multipart_uploads (
    id            UUID          PRIMARY KEY,
    -- The tenant is reached through the bucket, as it is for objects: every
    -- lookup joins the upload id to a bucket the caller's token owns, so an
    -- upload id on its own authorizes nothing (ADR 12).
    bucket_id     UUID          NOT NULL REFERENCES buckets (id),
    object_key    VARCHAR(1024) NOT NULL,
    content_type  VARCHAR(255)  NOT NULL,
    user_metadata JSONB         NOT NULL DEFAULT '{}'::JSONB,
    created_at    TIMESTAMPTZ   NOT NULL,
    expires_at    TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_multipart_uploads_expiry_after_creation CHECK (expires_at > created_at)
);

-- Serves the expiry sweep, which deletes the oldest expired sessions in batches.
CREATE INDEX ix_multipart_uploads_expires_at ON multipart_uploads (expires_at);

-- Serves listing a bucket's open uploads, and keeps the foreign key to buckets
-- from forcing a sequential scan when a bucket row is touched.
CREATE INDEX ix_multipart_uploads_bucket ON multipart_uploads (bucket_id, object_key);

-- One row per chunk of each uploaded part. A part is a list of chunks, cut
-- independently of every other part, so a part's last chunk may be short (ADR 12).
--
-- A part is known only through its chunks: there is no row for a part with no
-- chunks, which is why an empty part is rejected rather than recorded.
CREATE TABLE multipart_upload_chunks (
    upload_id    UUID        NOT NULL REFERENCES multipart_uploads (id) ON DELETE CASCADE,
    part_number  INTEGER     NOT NULL,
    chunk_index  INTEGER     NOT NULL,
    chunk_digest VARCHAR(64) NOT NULL,
    size_bytes   BIGINT      NOT NULL,
    -- Recorded, never recomputed, exactly as in object_chunks (ADR 11), and
    -- copied there unchanged on completion.
    node_id      VARCHAR(64) NOT NULL,
    PRIMARY KEY (upload_id, part_number, chunk_index),
    CONSTRAINT ck_multipart_upload_chunks_part_number CHECK (part_number BETWEEN 1 AND 10000),
    CONSTRAINT ck_multipart_upload_chunks_index_non_negative CHECK (chunk_index >= 0),
    CONSTRAINT ck_multipart_upload_chunks_size_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT ck_multipart_upload_chunks_digest_lowercase_hex CHECK (chunk_digest ~ '^[a-f0-9]{64}$')
);

-- Serves chunk refcounting in Phase 3. A chunk staged in an open session is
-- referenced even though no object points at it yet; GC must count these rows
-- alongside object_chunks before it reclaims any bytes, or it would delete a
-- part out from under an upload that is about to complete.
CREATE INDEX ix_multipart_upload_chunks_digest ON multipart_upload_chunks (chunk_digest);

-- ON DELETE CASCADE above is what makes completing, aborting and expiring a
-- session one statement each: deleting the session row takes its staged chunks
-- with it. Chunks a completed object kept are in object_chunks by then; chunks
-- of parts it left out become unreferenced bytes for GC, like any other orphan.
