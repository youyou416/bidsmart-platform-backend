package com.bidsmart.platform.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Versioned PBKDF2-HMAC-SM3. The default count is an engineering parameter. */
public final class Sm3PasswordEncoder implements PasswordEncoder {
    public static final int DEFAULT_ITERATIONS = 600_000;
    private static final int MAX_ITERATIONS = 5_000_000;
    private final int iterations;
    private final SecureRandom random;

    public Sm3PasswordEncoder() { this(DEFAULT_ITERATIONS); }
    public Sm3PasswordEncoder(int iterations) { this(iterations, new SecureRandom()); }
    Sm3PasswordEncoder(int iterations, SecureRandom random) {
        if (iterations < 1_000 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("PBKDF2 iteration count out of supported range");
        }
        this.iterations = iterations;
        this.random = random;
        Sm3.selfTest();
    }

    @Override public String encode(CharSequence rawPassword) {
        if (!validPasswordEncoding(rawPassword)) {
            throw new IllegalArgumentException("Password missing or too long");
        }
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] password = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        try {
            byte[] result = derive(password, salt, iterations);
            return "$pbkdf2-sm3$v=1$i=" + iterations + "$l=32$"
                + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(result);
        } finally { Arrays.fill(password, (byte) 0); }
    }

    @Override public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (!validPasswordEncoding(rawPassword)) { return false; }
        Parameters parameters = parse(encodedPassword);
        if (parameters == null) { return false; }
        byte[] password = rawPassword.toString().getBytes(StandardCharsets.UTF_8);
        byte[] candidate = null;
        try {
            candidate = derive(password, parameters.salt, parameters.iterations);
            return MessageDigest.isEqual(parameters.hash, candidate);
        } finally {
            Arrays.fill(password, (byte) 0);
            if (candidate != null) { Arrays.fill(candidate, (byte) 0); }
        }
    }

    @Override public boolean upgradeEncoding(String encodedPassword) {
        Parameters parameters = parse(encodedPassword);
        return parameters == null || parameters.iterations < iterations;
    }

    public static byte[] derive(byte[] password, byte[] salt, int iterations) {
        if (password == null || salt == null || iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Invalid PBKDF2 parameters");
        }
        PKCS5S2ParametersGenerator generator = new PKCS5S2ParametersGenerator(new SM3Digest());
        generator.init(password, salt, iterations);
        return ((KeyParameter) generator.generateDerivedParameters(256)).getKey();
    }

    private static Parameters parse(String encoded) {
        if (encoded == null || encoded.length() > 1024) { return null; }
        try {
            String[] parts = encoded.split("\\$", -1);
            if (parts.length != 7 || !parts[0].isEmpty() || !"pbkdf2-sm3".equals(parts[1])
                    || !"v=1".equals(parts[2]) || !parts[3].matches("i=[1-9][0-9]{0,6}")
                    || !"l=32".equals(parts[4])) { return null; }
            int count = Integer.parseInt(parts[3].substring(2));
            if (count < 1000 || count > MAX_ITERATIONS) { return null; }
            byte[] salt = Base64.getDecoder().decode(parts[5]);
            byte[] hash = Base64.getDecoder().decode(parts[6]);
            if (salt.length < 16 || salt.length > 64 || hash.length != 32) { return null; }
            return new Parameters(count, salt, hash);
        } catch (IllegalArgumentException invalid) { return null; }
    }

    private static boolean validPasswordEncoding(CharSequence password) {
        if (password == null) { return false; }
        String value = password.toString();
        if (value.codePointCount(0, value.length()) > 128) { return false; }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) { return false; }
            } else if (Character.isLowSurrogate(current)) { return false; }
        }
        return true;
    }

    private static final class Parameters {
        private final int iterations;
        private final byte[] salt;
        private final byte[] hash;
        private Parameters(int iterations, byte[] salt, byte[] hash) {
            this.iterations = iterations; this.salt = salt; this.hash = hash;
        }
    }
}
