package com.bidsmart.platform.crypto;

/** Implementations must never return the same 96-bit nonce for the same key. */
public interface NonceAllocator {
    byte[] allocate(String keyId, byte[] key);
}
