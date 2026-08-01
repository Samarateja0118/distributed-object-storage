package com.dfs.metadata;

import com.dfs.common.hash.Sha256;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises V2's backfill against data that looks like Phase 1's.
 *
 * <p>The other integration tests start from an empty database, where the
 * backfill's {@code INSERT ... SELECT} matches no rows and therefore proves
 * nothing. This one migrates to V1, writes objects the way Phase 1 did, and only
 * then applies V2 — which is the only arrangement in which the interesting part
 * of that migration runs at all.
 *
 * <p>It uses its own schema rather than the one the Spring context owns, so
 * stepping the migration version by hand cannot disturb the other suites.
 */
class ChunkBackfillMigrationIT extends AbstractMetadataIT {

    private static final String SCHEMA = "backfill_check";

    private static final UUID BUCKET_ID = UUID.randomUUID();
    private static final UUID SIZED_OBJECT = UUID.randomUUID();
    private static final UUID EMPTY_OBJECT = UUID.randomUUID();

    private static final String CONTENT_HASH = "d".repeat(64);
    private static final String EMPTY_HASH = Sha256.hexOfContent(new byte[0]);

    @BeforeAll
    static void migrateAcrossThePhaseBoundary() throws Exception {
        DataSource dataSource = dataSource();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }

        // Phase 1's schema, and nothing beyond it.
        flyway(dataSource).target("1").load().migrate();
        seedPhaseOneObjects(dataSource);

        // The upgrade under test.
        flyway(dataSource).load().migrate();
    }

    @Test
    void givesEveryPhaseOneObjectASingleChunkNamedByItsContentHash() throws Exception {
        List<Chunk> chunks = chunksOf(SIZED_OBJECT);

        assertThat(chunks).hasSize(1);
        Chunk only = chunks.getFirst();
        assertThat(only.index()).isZero();
        assertThat(only.sizeBytes()).isEqualTo(1024);
        // The digest is the object's old content hash, which is also the name the
        // bytes are already filed under on disk — so the object downloads through
        // the chunked path without anything moving.
        assertThat(only.digest()).isEqualTo(CONTENT_HASH);
        assertThat(only.nodeId()).isEqualTo("blob-node-0");
    }

    /**
     * A zero-byte object has no bytes, so it gets no chunk row — and its Phase 1
     * content hash already equals the composite of an empty list, which is the
     * one case where the old and new meanings of the column agree.
     */
    @Test
    void leavesZeroByteObjectsWithoutChunks() throws Exception {
        assertThat(chunksOf(EMPTY_OBJECT)).isEmpty();
        assertThat(Sha256.composite(List.of())).isEqualTo(EMPTY_HASH);
    }

    @Test
    void preservesTheEtagsClientsAlreadyHold() throws Exception {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT content_hash, etag FROM " + SCHEMA + ".objects WHERE id = '" + SIZED_OBJECT + "'")) {
            assertThat(rows.next()).isTrue();
            // Deliberately not rewritten to the composite form: doing so would
            // invalidate any ETag a client had stored.
            assertThat(rows.getString("content_hash")).isEqualTo(CONTENT_HASH);
            assertThat(rows.getString("etag")).isEqualTo(CONTENT_HASH);
        }
    }

    private static void seedPhaseOneObjects(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO %s.buckets (id, tenant_id, name, versioning_enabled, default_encryption,
                                            created_at, updated_at)
                    VALUES ('%s', '%s', 'legacy', FALSE, 'NONE', NOW(), NOW())
                    """.formatted(SCHEMA, BUCKET_ID, UUID.randomUUID()));
            statement.execute(phaseOneObject(SIZED_OBJECT, "report.pdf", 1024, CONTENT_HASH));
            statement.execute(phaseOneObject(EMPTY_OBJECT, "empty.txt", 0, EMPTY_HASH));
        }
    }

    private static String phaseOneObject(UUID id, String key, long sizeBytes, String contentHash) {
        return """
                INSERT INTO %s.objects (id, bucket_id, object_key, version_id, is_latest, size_bytes,
                                        content_type, content_hash, etag, user_metadata, created_at, updated_at)
                VALUES ('%s', '%s', '%s', '%s', TRUE, %d, 'application/octet-stream', '%s', '%s',
                        '{}'::JSONB, NOW(), NOW())
                """.formatted(SCHEMA, id, BUCKET_ID, key, UUID.randomUUID(), sizeBytes, contentHash, contentHash);
    }

    private List<Chunk> chunksOf(UUID objectId) throws Exception {
        List<Chunk> chunks = new ArrayList<>();
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT chunk_index, chunk_digest, size_bytes, node_id
                       FROM %s.object_chunks
                      WHERE object_id = '%s'
                      ORDER BY chunk_index
                     """.formatted(SCHEMA, objectId))) {
            while (rows.next()) {
                chunks.add(new Chunk(
                        rows.getInt("chunk_index"),
                        rows.getString("chunk_digest"),
                        rows.getLong("size_bytes"),
                        rows.getString("node_id")));
            }
        }
        return chunks;
    }

    private static org.flywaydb.core.api.configuration.FluentConfiguration flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .defaultSchema(SCHEMA)
                .locations("classpath:db/migration");
    }

    private static DataSource dataSource() {
        org.springframework.jdbc.datasource.DriverManagerDataSource source =
                new org.springframework.jdbc.datasource.DriverManagerDataSource();
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        return source;
    }

    private record Chunk(int index, String digest, long sizeBytes, String nodeId) {
    }
}
