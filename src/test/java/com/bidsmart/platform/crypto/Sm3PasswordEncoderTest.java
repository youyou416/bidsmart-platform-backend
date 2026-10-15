package com.bidsmart.platform.crypto;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class Sm3PasswordEncoderTest {
    private final Sm3PasswordEncoder encoder = new Sm3PasswordEncoder(1000);

    @Test void publishedSm3Vector() {
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", hex(Sm3.digest(bytes("abc"))));
        Sm3.selfTest();
    }

    /** Independently generated with Python hashlib backed by OpenSSL 3.5.7, not BC. */
    @Test void independentOpenSslPbkdf2Vectors() {
        assertEquals("4612f922a1fdcefaf4312fc6f8f3322b489cbf24f2ea361b44c2bd8fa2c6dcb0",
            hex(Sm3PasswordEncoder.derive(bytes("password"), bytes("salt"), 1)));
        assertEquals("fee723a2bc966e11dffb66133f4e8df577383c78ade30e3298edbd3e54ed85b7",
            hex(Sm3PasswordEncoder.derive(bytes("password"), bytes("salt"), 2)));
    }

    @Test void calibratesDefaultAgainstIndependentOpenSslVector() {
        long started = System.nanoTime();
        byte[] result = Sm3PasswordEncoder.derive(bytes("password"), bytes("0123456789abcdef"), 600000);
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        assertEquals("b8a186980a0e7f40a08a274680d9601a33f2447b98d7bb7f2ed467b91862beb7", hex(result));
        System.out.println("PBKDF2-HMAC-SM3 calibration: iterations=600000, derivedBytes=32, elapsedMs=" + elapsed);
    }

    @Test void randomSaltsAndWrongPassword() {
        String first = encoder.encode(" 合成Unicode密码🔐 ");
        String second = encoder.encode(" 合成Unicode密码🔐 ");
        assertNotEquals(first, second);
        assertTrue(encoder.matches(" 合成Unicode密码🔐 ", first));
        assertFalse(encoder.matches("合成Unicode密码🔐", first));
        assertFalse(encoder.matches("wrong-password", first));
        assertTrue(first.startsWith("$pbkdf2-sm3$v=1$i=1000$l=32$"));
        assertTrue(java.util.Base64.getDecoder().decode(first.split("\\$")[5]).length >= 16);
    }

    @Test void accepts128UnicodeCodePointsWithoutTruncation() {
        String password = "🔐".repeat(128);
        String encoded = encoder.encode(password);
        assertTrue(encoder.matches(password, encoded));
        assertFalse(encoder.matches(password + "x", encoded));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(password + "x"));
        assertThrows(IllegalArgumentException.class, () -> encoder.encode("bad\uD800"));
        assertFalse(encoder.matches("bad\uD800", encoded));
    }

    @Test void rejectsUnknownAlgorithmsVersionsMalformedOrExcessiveParameters() {
        String encoded = encoder.encode("synthetic-password");
        assertFalse(encoder.matches("synthetic-password", encoded.replace("v=1", "v=2")));
        assertFalse(encoder.matches("synthetic-password", encoded.replace("pbkdf2-sm3", "sm3")));
        assertFalse(encoder.matches("synthetic-password", encoded.replace("l=32", "l=16")));
        assertFalse(encoder.matches("synthetic-password", encoded.replace("i=1000", "i=9999999")));
        assertFalse(encoder.matches("synthetic-password", encoded.replace("i=1000", "i=1")));
        assertFalse(encoder.matches("synthetic-password", "$pbkdf2-sm3$v=1$i=1000$l=32$bad$bad"));
        assertFalse(encoder.matches("synthetic-password", null));
        assertFalse(encoder.matches(null, encoded));
        assertFalse(encoder.matches("synthetic-password", encoded + "$extra"));
        assertThrows(IllegalArgumentException.class, () -> new Sm3PasswordEncoder(0));
        assertTrue(new Sm3PasswordEncoder().upgradeEncoding(encoded));
        assertFalse(encoder.upgradeEncoding(encoded));
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder();
        for (byte item : bytes) { value.append(String.format("%02x", item & 255)); }
        return value.toString();
    }
}
