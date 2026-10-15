package com.bidsmart.platform.crypto;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Sm4GcmCipherTest {
    @TempDir Path state;
    private static final byte[] AAD = "synthetic:user:42:privateField".getBytes(StandardCharsets.UTF_8);
    private static byte[] key() { byte[] bytes = new byte[16]; new SecureRandom().nextBytes(bytes); return bytes; }
    private Sm4GcmCipher cipher(String id, byte[] key) {
        return new Sm4GcmCipher(Collections.singletonMap(id, key), id, new FileNonceAllocator(state));
    }

    /** RFC 8998 Appendix A.1, cross-checked with OpenSSL's evpciph_sm4.txt fixture. */
    @Test void publishedRfc8998Sm4GcmVector() throws Exception {
        GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
        cipher.init(true, new AEADParameters(new KeyParameter(hex("0123456789abcdeffedcba9876543210")), 128,
            hex("00001234567800000000abcd"), hex("feedfacedeadbeeffeedfacedeadbeefabaddad2")));
        byte[] plaintext = hex("aaaaaaaaaaaaaaaabbbbbbbbbbbbbbbbccccccccccccccccdddddddddddddddd"
            + "eeeeeeeeeeeeeeeeffffffffffffffffeeeeeeeeeeeeeeeeaaaaaaaaaaaaaaaa");
        byte[] result = new byte[cipher.getOutputSize(plaintext.length)];
        int size = cipher.processBytes(plaintext, 0, plaintext.length, result, 0);
        cipher.doFinal(result, size);
        assertArrayEquals(hex("17f399f08c67d5ee19d0dc9969c4bb7d5fd46fd3756489069157b282bb200735"
            + "d82710ca5c22f0ccfa7cbf93d496ac15a56834cbcf98c397b4024a2691233b8d"
            + "83de3541e4c2b58177e065a9bf7b62ec"), result);
        Sm4GcmCipher.selfTest();
    }

    @Test void roundTripUsesNewNonceAndAuthenticatesAad() {
        Sm4GcmCipher cipher = cipher("test-one", key());
        byte[] plaintext = "仅合成测试数据🔐".getBytes(StandardCharsets.UTF_8);
        Sm4GcmEnvelope first = cipher.encrypt(plaintext, AAD);
        Sm4GcmEnvelope second = cipher.encrypt(plaintext, AAD);
        assertNotEquals(first.getNonce(), second.getNonce());
        assertArrayEquals(plaintext, cipher.decrypt(first, AAD));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(first, "other-record".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> cipher.encrypt(plaintext, new byte[0]));
    }

    @Test void rotationDecryptsOldKeyIdAndRejectsWrongOrMissingKey() {
        byte[] oldKey = key(); byte[] newKey = key();
        Sm4GcmEnvelope old = cipher("old-test", oldKey).encrypt(new byte[]{1,2,3}, AAD);
        Map<String, byte[]> ring = new LinkedHashMap<>(); ring.put("old-test", oldKey); ring.put("new-test", newKey);
        Sm4GcmCipher rotated = new Sm4GcmCipher(ring, "new-test", new FileNonceAllocator(state));
        assertArrayEquals(new byte[]{1,2,3}, rotated.decrypt(old, AAD));
        assertEquals("new-test", rotated.encrypt(new byte[]{4}, AAD).getKeyId());
        assertThrows(IllegalArgumentException.class, () -> cipher("old-test", key()).decrypt(old, AAD));
        assertThrows(IllegalArgumentException.class, () -> cipher("only-new", newKey).decrypt(old, AAD));
        assertThrows(IllegalArgumentException.class, () -> new Sm4GcmCipher(Collections.emptyMap(), "missing", new FileNonceAllocator(state)));
        assertThrows(IllegalArgumentException.class, () -> new Sm4GcmCipher(Collections.singletonMap("invalid", new byte[15]), "invalid", new FileNonceAllocator(state)));
    }

    @Test void ciphertextTagNonceAndEnvelopeMetadataTamperingFail() {
        byte[] key = key(); Sm4GcmCipher cipher = cipher("test-one", key);
        Sm4GcmEnvelope envelope = cipher.encrypt(new byte[]{1,2,3}, AAD);
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new Sm4GcmEnvelope("SM4-GCM", 1, "test-one",
            envelope.getNonce(), flip(envelope.getCiphertext()), envelope.getTag()), AAD));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new Sm4GcmEnvelope("SM4-GCM", 1, "test-one",
            envelope.getNonce(), envelope.getCiphertext(), flip(envelope.getTag())), AAD));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new Sm4GcmEnvelope("SM4-GCM", 1, "test-one",
            flip(envelope.getNonce()), envelope.getCiphertext(), envelope.getTag()), AAD));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new Sm4GcmEnvelope("SM4-ECB", 1, "test-one",
            envelope.getNonce(), envelope.getCiphertext(), envelope.getTag()), AAD));
        assertThrows(IllegalArgumentException.class, () -> cipher.decrypt(new Sm4GcmEnvelope("SM4-GCM", 2, "test-one",
            envelope.getNonce(), envelope.getCiphertext(), envelope.getTag()), AAD));
        Sm4GcmCipher sameKeyAlias = cipher("alias-test", key);
        assertThrows(IllegalArgumentException.class, () -> sameKeyAlias.decrypt(new Sm4GcmEnvelope("SM4-GCM", 1, "alias-test",
            envelope.getNonce(), envelope.getCiphertext(), envelope.getTag()), AAD));
    }

    @Test void persistentCounterSurvivesNewInstancesAndAliases() {
        byte[] key = key();
        Sm4GcmEnvelope first = cipher("id-one", key).encrypt(new byte[]{1}, AAD);
        Sm4GcmEnvelope next = cipher("id-one", key).encrypt(new byte[]{1}, AAD);
        Sm4GcmEnvelope alias = cipher("id-two", key).encrypt(new byte[]{1}, AAD);
        assertNotEquals(first.getNonce(), next.getNonce()); assertNotEquals(next.getNonce(), alias.getNonce());
        Map<String, byte[]> duplicates = new LinkedHashMap<>(); duplicates.put("one", key); duplicates.put("two", key.clone());
        assertThrows(IllegalArgumentException.class, () -> new Sm4GcmCipher(duplicates, "one", new FileNonceAllocator(state)));
    }

    @Test void concurrentInstancesNeverReuseNonce() throws Exception {
        byte[] key = key(); ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            java.util.List<Callable<String>> calls = new java.util.ArrayList<>();
            for (int i=0; i<80; i++) { calls.add(() -> cipher("test-key", key).encrypt(new byte[]{1}, AAD).getNonce()); }
            Set<String> nonces = new HashSet<>();
            for (Future<String> result : executor.invokeAll(calls)) { assertTrue(nonces.add(result.get())); }
            assertEquals(80, nonces.size());
        } finally { executor.shutdownNow(); }
    }

    @Test void corruptPersistentStateFailsClosed() throws Exception {
        byte[] key = key(); cipher("test-key", key).encrypt(new byte[]{1}, AAD);
        Path file;
        try (java.util.stream.Stream<Path> files = Files.list(state)) { file = files.findFirst().orElseThrow(); }
        byte[] contents = Files.readAllBytes(file); contents[47] ^= 1; Files.write(file, contents);
        assertThrows(IllegalStateException.class, () -> cipher("test-key", key).encrypt(new byte[]{1}, AAD));
    }

    private static String flip(String value) {
        byte[] bytes = Base64.getDecoder().decode(value); bytes[0] ^= 1;
        return Base64.getEncoder().encodeToString(bytes);
    }
    private static byte[] hex(String value) {
        byte[] result = new byte[value.length()/2];
        for (int i=0; i<result.length; i++) { result[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16); }
        return result;
    }
}
