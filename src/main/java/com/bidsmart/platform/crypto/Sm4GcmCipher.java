package com.bidsmart.platform.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

/** Explicitly injected key ring; no built-in key or automatic plaintext fallback. */
public final class Sm4GcmCipher {
    private static final String ALGORITHM = "SM4-GCM";
    private final Map<String, byte[]> keys;
    private final String activeKeyId;
    private final NonceAllocator nonces;

    public Sm4GcmCipher(Map<String, byte[]> keys, String activeKeyId, NonceAllocator nonces) {
        if (keys == null || keys.isEmpty() || activeKeyId == null || nonces == null) {
            throw new IllegalArgumentException("SM4 requires an injected key ring, active key id, and nonce allocator");
        }
        Map<String, byte[]> checked = new LinkedHashMap<>();
        keys.forEach((id, key) -> {
            if (id == null || !id.matches("[A-Za-z0-9_.-]{1,64}") || key == null || key.length != 16) {
                throw new IllegalArgumentException("Invalid SM4 key id or key length");
            }
            for (byte[] existing : checked.values()) {
                if (java.security.MessageDigest.isEqual(existing, key)) {
                    throw new IllegalArgumentException("Different key ids must not share key material");
                }
            }
            checked.put(id, key.clone());
        });
        if (!checked.containsKey(activeKeyId)) { throw new IllegalArgumentException("Active SM4 key is missing"); }
        this.keys = Collections.unmodifiableMap(checked);
        this.activeKeyId = activeKeyId;
        this.nonces = nonces;
        selfTest();
    }

    public Sm4GcmEnvelope encrypt(byte[] plaintext, byte[] aad) {
        if (plaintext == null || aad == null || aad.length == 0) {
            throw new IllegalArgumentException("Plaintext and field/record-bound AAD are required");
        }
        byte[] key = keys.get(activeKeyId);
        byte[] nonce = nonces.allocate(activeKeyId, key.clone());
        if (nonce == null || nonce.length != 12) { throw new IllegalStateException("Nonce must be 96 bits"); }
        byte[] result = crypt(true, key, nonce, metadataAad(activeKeyId, aad), plaintext);
        Base64.Encoder encoder = Base64.getEncoder();
        return new Sm4GcmEnvelope(ALGORITHM, 1, activeKeyId, encoder.encodeToString(nonce),
            encoder.encodeToString(Arrays.copyOf(result, result.length - 16)),
            encoder.encodeToString(Arrays.copyOfRange(result, result.length - 16, result.length)));
    }

    public byte[] decrypt(Sm4GcmEnvelope envelope, byte[] aad) {
        if (envelope == null || aad == null || aad.length == 0 || !ALGORITHM.equals(envelope.getAlgorithm())
                || envelope.getVersion() != 1 || !keys.containsKey(envelope.getKeyId())) {
            throw new IllegalArgumentException("Invalid SM4 envelope, AAD, or unavailable key id");
        }
        try {
            byte[] nonce = envelope.nonceBytes();
            byte[] tag = envelope.tagBytes();
            byte[] ciphertext = envelope.ciphertextBytes();
            if (nonce.length != 12 || tag.length != 16) { throw new IllegalArgumentException("Invalid SM4 envelope lengths"); }
            byte[] input = Arrays.copyOf(ciphertext, ciphertext.length + tag.length);
            System.arraycopy(tag, 0, input, ciphertext.length, tag.length);
            return crypt(false, keys.get(envelope.getKeyId()), nonce, metadataAad(envelope.getKeyId(), aad), input);
        } catch (NullPointerException failure) {
            throw new IllegalArgumentException("Invalid SM4 envelope", failure);
        }
    }

    private static byte[] metadataAad(String keyId, byte[] aad) {
        byte[] metadata = (ALGORITHM + "|1|" + keyId).getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(4 + metadata.length + aad.length).putInt(metadata.length).put(metadata).put(aad).array();
    }

    private static byte[] crypt(boolean encrypt, byte[] key, byte[] nonce, byte[] aad, byte[] input) {
        GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
        cipher.init(encrypt, new AEADParameters(new KeyParameter(key), 128, nonce, aad));
        byte[] output = new byte[cipher.getOutputSize(input.length)];
        try {
            int size = cipher.processBytes(input, 0, input.length, output, 0);
            size += cipher.doFinal(output, size);
            return Arrays.copyOf(output, size);
        } catch (InvalidCipherTextException authenticationFailure) {
            Arrays.fill(output, (byte) 0);
            throw new IllegalArgumentException("SM4 authentication failed", authenticationFailure);
        }
    }

    /** Standard SM4 block known-answer test, prerequisite for the GCM implementation. */
    public static void selfTest() {
        byte[] input = hex("0123456789abcdeffedcba9876543210");
        byte[] expected = hex("681edf34d206965e86b3e94f536e4246");
        SM4Engine engine = new SM4Engine();
        engine.init(true, new KeyParameter(input));
        byte[] result = new byte[16]; engine.processBlock(input, 0, result, 0);
        if (!java.security.MessageDigest.isEqual(result, expected)) { throw new IllegalStateException("SM4 self-test failed"); }
    }
    private static byte[] hex(String value) {
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) { result[i] = (byte) Integer.parseInt(value.substring(2*i, 2*i+2), 16); }
        return result;
    }
}
