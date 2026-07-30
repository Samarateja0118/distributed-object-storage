package com.dfs.blob.infrastructure.adapter.out.filesystem;

import com.dfs.blob.application.port.out.BlobStorePort;
import com.dfs.blob.domain.exception.BlobDigestMismatchException;
import com.dfs.blob.domain.exception.BlobNotFoundException;
import com.dfs.blob.domain.model.BlobDigest;
import com.dfs.blob.domain.model.StoredBlob;
import com.dfs.common.hash.Sha256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Stores blobs as files on local disk, sharded two levels deep by digest prefix.
 */
@Component
class LocalBlobStoreAdapter implements BlobStorePort {

    private static final Logger log = LoggerFactory.getLogger(LocalBlobStoreAdapter.class);

    private final Path storageRoot;
    private final Path stagingDir;

    LocalBlobStoreAdapter(BlobStorageLayout layout) {
        this.storageRoot = layout.root();
        this.stagingDir = layout.staging();
    }

    /**
     * Writes to a staging file, verifies the digest, then publishes with an atomic
     * rename.
     *
     * <p>The order matters. Streaming straight to the final path would make a
     * partially written blob readable, and readers cannot tell a truncated file
     * from a complete one — the digest is the file's name, not something they
     * re-check on every read. Staging plus rename means a blob is either absent or
     * whole.
     */
    @Override
    public StoredBlob write(BlobDigest digest, InputStream content) {
        Path target = pathFor(digest);
        Optional<StoredBlob> existing = stat(digest);
        if (existing.isPresent()) {
            // Same name means same bytes, so the write is already done.
            return existing.get();
        }

        Path staged = null;
        try {
            staged = Files.createTempFile(stagingDir, "incoming-", ".part");
            long sizeBytes = drainAndVerify(digest, content, staged);
            publish(staged, target);
            staged = null;
            return new StoredBlob(digest, sizeBytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store blob " + digest, e);
        } finally {
            discard(staged);
        }
    }

    @Override
    public Optional<StoredBlob> stat(BlobDigest digest) {
        Path path = pathFor(digest);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StoredBlob(digest, Files.size(path)));
        } catch (NoSuchFileException e) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stat blob " + digest, e);
        }
    }

    @Override
    public InputStream openStream(BlobDigest digest) {
        try {
            return Files.newInputStream(pathFor(digest), StandardOpenOption.READ);
        } catch (NoSuchFileException e) {
            throw new BlobNotFoundException(digest);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read blob " + digest, e);
        }
    }

    /**
     * Copies the stream to the staging file while hashing it, and forces it to
     * disk before returning.
     *
     * <p>The fsync is the difference between "the OS has the bytes" and "the disk
     * has the bytes". Without it a power loss after the response could leave
     * metadata pointing at content that no longer exists — a dangling object,
     * which is the worst outcome for a storage system because nothing detects it
     * until a read fails.
     */
    private long drainAndVerify(BlobDigest expected, InputStream content, Path staged) throws IOException {
        MessageDigest digest = Sha256.newDigest();
        long sizeBytes;
        try (FileChannel channel = FileChannel.open(staged, StandardOpenOption.WRITE);
             OutputStream out = Channels.newOutputStream(channel);
             DigestOutputStream hashing = new DigestOutputStream(out, digest)) {
            sizeBytes = content.transferTo(hashing);
            hashing.flush();
            channel.force(true);
        }

        String actual = Sha256.hexOf(digest);
        if (!actual.equals(expected.value())) {
            throw new BlobDigestMismatchException(expected, actual);
        }
        return sizeBytes;
    }

    private void publish(Path staged, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException e) {
            // A concurrent write of identical content published first; its bytes
            // are ours by definition, so this is success, not a conflict.
            Files.deleteIfExists(staged);
        }
    }

    private void discard(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException e) {
            // Leaking a staging file is not worth failing a request over; it is
            // recoverable, unlike the original error this would mask.
            log.warn("Could not remove staging file {}", staged, e);
        }
    }

    private Path pathFor(BlobDigest digest) {
        return storageRoot
                .resolve(digest.firstShard())
                .resolve(digest.secondShard())
                .resolve(digest.value());
    }
}
