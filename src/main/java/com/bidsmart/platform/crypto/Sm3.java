package com.bidsmart.platform.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.bouncycastle.crypto.digests.SM3Digest;

/** SM3 is a digest utility, never a password-storage algorithm. */
public final class Sm3 {
    private static final byte[] ABC_VECTOR = hex("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0");
    private Sm3() { }
    public static byte[] digest(byte[] input) {
        SM3Digest digest = new SM3Digest();
        digest.update(input, 0, input.length);
        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return result;
    }
    public static void selfTest() {
        if (!Arrays.equals(ABC_VECTOR, digest("abc".getBytes(StandardCharsets.US_ASCII)))) {
            throw new IllegalStateException("SM3 self-test failed");
        }
    }
    private static byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
