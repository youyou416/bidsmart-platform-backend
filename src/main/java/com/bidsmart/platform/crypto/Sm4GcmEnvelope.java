package com.bidsmart.platform.crypto;

import java.util.Base64;

/** Public transport/storage format. The authentication tag is always 128 bits. */
public final class Sm4GcmEnvelope {
    private final String algorithm;
    private final int version;
    private final String keyId;
    private final String nonce;
    private final String ciphertext;
    private final String tag;
    public Sm4GcmEnvelope(String algorithm, int version, String keyId, String nonce, String ciphertext, String tag) {
        this.algorithm = algorithm; this.version = version; this.keyId = keyId;
        this.nonce = nonce; this.ciphertext = ciphertext; this.tag = tag;
    }
    public String getAlgorithm() { return algorithm; }
    public int getVersion() { return version; }
    public String getKeyId() { return keyId; }
    public String getNonce() { return nonce; }
    public String getCiphertext() { return ciphertext; }
    public String getTag() { return tag; }
    byte[] nonceBytes() { return Base64.getDecoder().decode(nonce); }
    byte[] ciphertextBytes() { return Base64.getDecoder().decode(ciphertext); }
    byte[] tagBytes() { return Base64.getDecoder().decode(tag); }
}
