package com.dfs.integration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A real DFS cluster: four services running as separate JVMs from their own
 * repackaged jars, against a real PostgreSQL and Redis.
 *
 * <p>Every other integration suite in this repository tests one service with its
 * neighbours stubbed. That is the right shape for those tests, but it leaves the
 * cross-service contracts — blob-first ordering, metadata-first reads, and token
 * forwarding — verified only against stand-ins written from the same assumptions
 * as the code they stand in for. A stub agrees with the code whether or not the
 * code is right.
 *
 * <p>The services run as processes rather than in this JVM for two reasons. The
 * jars are repackaged, so their classes live under {@code BOOT-INF/} and are not
 * importable here anyway. More usefully, a packaged jar is what actually ships,
 * and packaging is where auto-configuration failures surface — a service can
 * pass every in-process test and still fail to start as a jar.
 *
 * <p>Started once per JVM from a static holder rather than per class: a shared
 * {@code @Container} is stopped after the first test class finishes, and Ryuk
 * reaps the containers at exit regardless.
 */
final class DfsCluster {

    /**
     * Every service validates tokens with this and only the auth service mints
     * them, which is the point of the shared contract in dfs-common. HS256 needs
     * at least 256 bits of key material.
     */
    private static final String JWT_SIGNING_SECRET = "dfs-integration-test-signing-secret-please-ignore";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(120);

    private static final Path REPO_ROOT = Path.of(System.getProperty("dfs.repo.root", "..")).toAbsolutePath().normalize();
    private static final String VERSION = System.getProperty("dfs.project.version", "0.1.0-SNAPSHOT");

    private static final List<Process> PROCESSES = new ArrayList<>();

    private static PostgreSQLContainer<?> postgres;
    private static GenericContainer<?> redis;
    private static Path blobRoot;
    private static Path logDir;

    private static String authUrl;
    private static String metadataUrl;
    private static String blobUrl;
    private static String orchestratorUrl;

    private DfsCluster() {
    }

    private static final class Holder {
        static {
            try {
                start();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start the DFS cluster", e);
            }
        }

        static void touch() {
            // Referencing the class triggers the static initializer exactly once.
        }
    }

    static void ensureRunning() {
        Holder.touch();
    }

    static String authUrl() {
        ensureRunning();
        return authUrl;
    }

    static String metadataUrl() {
        ensureRunning();
        return metadataUrl;
    }

    static String blobUrl() {
        ensureRunning();
        return blobUrl;
    }

    static String orchestratorUrl() {
        ensureRunning();
        return orchestratorUrl;
    }

    /** The blob node's storage root, so tests can assert on what is actually on disk. */
    static Path blobRoot() {
        ensureRunning();
        return blobRoot;
    }

    private static void start() throws Exception {
        blobRoot = Files.createTempDirectory("dfs-e2e-blobs");
        logDir = Path.of("target", "e2e-logs");
        Files.createDirectories(logDir);

        // The same init script the compose stack uses, so the database layout under
        // test is the one that ships rather than one invented here.
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("dfs")
                .withUsername("dfs")
                .withPassword("dfs")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(REPO_ROOT.resolve("deploy/postgres/init-databases.sql")),
                        "/docker-entrypoint-initdb.d/init-databases.sql");
        postgres.start();

        // Not used by any Phase 1 code path, but auth, metadata, and the
        // orchestrator all pull in the Redis starter, and its health indicator
        // keeps /actuator/health from reporting UP without it.
        redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redis.start();

        int authPort = freePort();
        int metadataPort = freePort();
        int blobPort = freePort();
        int orchestratorPort = freePort();
        int metadataGrpcPort = freePort();
        int blobGrpcPort = freePort();

        authUrl = "http://localhost:" + authPort;
        metadataUrl = "http://localhost:" + metadataPort;
        blobUrl = "http://localhost:" + blobPort;
        orchestratorUrl = "http://localhost:" + orchestratorPort;

        launch("dfs-auth-service", authPort, Map.of(
                "POSTGRES_DB", "dfs_auth"));

        launch("dfs-metadata-service", metadataPort, Map.of(
                "POSTGRES_DB", "dfs_metadata",
                "GRPC_PORT", String.valueOf(metadataGrpcPort)));

        launch("dfs-blob-node", blobPort, Map.of(
                "BLOB_STORAGE_PATH", blobRoot.toString(),
                "GRPC_PORT", String.valueOf(blobGrpcPort)));

        // The byte path is gRPC and metadata is REST, so the orchestrator is
        // pointed at two different addresses on two different transports. The
        // blob node's HTTP port is still served, and still exercised directly by
        // the tests, but the orchestrator no longer calls it.
        launch("dfs-upload-orchestrator", orchestratorPort, Map.of(
                "BLOB_GRPC_TARGET", "static://localhost:" + blobGrpcPort,
                "METADATA_SERVICE_URL", metadataUrl));

        awaitHealthy("dfs-auth-service", authUrl);
        awaitHealthy("dfs-metadata-service", metadataUrl);
        awaitHealthy("dfs-blob-node", blobUrl);
        awaitHealthy("dfs-upload-orchestrator", orchestratorUrl);

        Runtime.getRuntime().addShutdownHook(new Thread(DfsCluster::stopProcesses));
    }

    private static void launch(String module, int port, Map<String, String> extraEnv) throws IOException {
        Path jar = REPO_ROOT.resolve(module).resolve("target").resolve(module + "-" + VERSION + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(
                    "Missing " + jar + ". These tests run the packaged services, so the module must be "
                            + "packaged first — run `mvn verify` from the reactor rather than this module alone.");
        }

        Map<String, String> env = new LinkedHashMap<>();
        env.put("SERVER_PORT", String.valueOf(port));
        env.put("JWT_SIGNING_SECRET", JWT_SIGNING_SECRET);
        env.put("POSTGRES_HOST", postgres.getHost());
        env.put("POSTGRES_PORT", String.valueOf(postgres.getMappedPort(5432)));
        env.put("POSTGRES_USER", "dfs");
        env.put("POSTGRES_PASSWORD", "dfs");
        env.put("REDIS_HOST", redis.getHost());
        env.put("REDIS_PORT", String.valueOf(redis.getMappedPort(6379)));
        env.putAll(extraEnv);

        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar", jar.toString());
        builder.environment().putAll(env);
        builder.redirectErrorStream(true);
        builder.redirectOutput(logDir.resolve(module + ".log").toFile());

        PROCESSES.add(builder.start());
    }

    private static void awaitHealthy(String module, String baseUrl) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/actuator/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
                    return;
                }
            } catch (IOException | InterruptedException ignored) {
                // Not listening yet.
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(module + " never became healthy. Log tail:\n" + logTail(module));
    }

    private static String logTail(String module) {
        try {
            List<String> lines = Files.readAllLines(logDir.resolve(module + ".log"));
            return String.join("\n", lines.subList(Math.max(0, lines.size() - 40), lines.size()));
        } catch (IOException e) {
            return "(no log available: " + e.getMessage() + ")";
        }
    }

    private static void stopProcesses() {
        for (Process process : PROCESSES) {
            process.destroy();
        }
        for (Process process : PROCESSES) {
            try {
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
