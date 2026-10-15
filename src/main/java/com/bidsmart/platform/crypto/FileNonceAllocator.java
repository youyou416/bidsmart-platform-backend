package com.bidsmart.platform.crypto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent counter allocation: lock, advance, fsync, then return the nonce.
 * Keep this directory outside the checkout, protect it, back it up with keys,
 * and never restore/delete counters while reusing a key. One keyId per key.
 * Each file contains public nonce state and a key fingerprint, never the key.
 */
public final class FileNonceAllocator implements NonceAllocator {
    private static final int STATE_LENGTH = 80;
    private static final ConcurrentHashMap<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private final Path directory;
    private final SecureRandom random = new SecureRandom();

    public FileNonceAllocator(Path directory) {
        if (directory == null) { throw new IllegalArgumentException("Nonce state directory is required"); }
        this.directory = directory.toAbsolutePath().normalize();
    }

    @Override public byte[] allocate(String keyId, byte[] key) {
        if (keyId == null || key == null || key.length != 16) {
            throw new IllegalArgumentException("SM4 key id and 128-bit key required");
        }
        // State follows key material, so aliases in separate cipher instances share one counter.
        String filename = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Sm3.digest(key)) + ".nonce";
        Path file = directory.resolve(filename);
        synchronized (LOCKS.computeIfAbsent(file, ignored -> new Object())) {
            try {
                Files.createDirectories(directory);
                if (Files.isSymbolicLink(directory) || Files.isSymbolicLink(file)) {
                    throw new IllegalStateException("Nonce state must not use symbolic links");
                }
                try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE,
                        StandardOpenOption.READ, StandardOpenOption.WRITE);
                        FileLock lock = channel.lock()) {
                    byte[] fingerprint = Sm3.digest(key);
                    byte[] prefix = new byte[4];
                    long counter;
                    if (channel.size() == 0) {
                        random.nextBytes(prefix);
                        counter = 0;
                    } else {
                        if (channel.size() != STATE_LENGTH) { throw new IllegalStateException("Invalid nonce state"); }
                        ByteBuffer input = ByteBuffer.allocate(STATE_LENGTH);
                        while (input.hasRemaining()) {
                            if (channel.read(input) < 0) { throw new IllegalStateException("Incomplete nonce state"); }
                        }
                        byte[] state = input.array();
                        if (!MessageDigest.isEqual(Arrays.copyOfRange(state, 48, 80),
                                Sm3.digest(Arrays.copyOf(state, 48)))) {
                            throw new IllegalStateException("Corrupt nonce state");
                        }
                        input.flip();
                        if (input.getInt() != 1) { throw new IllegalStateException("Unsupported nonce state version"); }
                        byte[] expectedKey = new byte[32]; input.get(expectedKey);
                        if (!MessageDigest.isEqual(fingerprint, expectedKey)) {
                            throw new IllegalStateException("A key id cannot be reassigned to another key");
                        }
                        input.get(prefix); counter = input.getLong();
                    }
                    if (counter < 0 || counter == Long.MAX_VALUE) {
                        throw new IllegalStateException("SM4 nonce counter exhausted; rotate the key");
                    }
                    ByteBuffer next = ByteBuffer.allocate(STATE_LENGTH);
                    next.putInt(1).put(fingerprint).put(prefix).putLong(counter + 1);
                    next.put(Sm3.digest(Arrays.copyOf(next.array(), 48))).flip();
                    channel.position(0);
                    while (next.hasRemaining()) { channel.write(next); }
                    channel.force(true);
                    // Persist a newly created directory entry before issuing its first nonce.
                    try (FileChannel directoryChannel = FileChannel.open(directory, StandardOpenOption.READ)) {
                        directoryChannel.force(true);
                    }
                    return ByteBuffer.allocate(12).put(prefix).putLong(counter).array();
                }
            } catch (IOException failure) {
                throw new IllegalStateException("SM4 nonce allocation failed", failure);
            }
        }
    }
}
