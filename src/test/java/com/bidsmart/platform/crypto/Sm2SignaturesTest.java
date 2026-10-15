package com.bidsmart.platform.crypto;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.junit.jupiter.api.Test;

class Sm2SignaturesTest {
    @Test void signatureAuthenticatesMessageAndExplicitUserId() {
        AsymmetricCipherKeyPair pair = pair("sm2p256v1");
        ECPrivateKeyParameters privateKey = (ECPrivateKeyParameters) pair.getPrivate();
        ECPublicKeyParameters publicKey = (ECPublicKeyParameters) pair.getPublic();
        byte[] message = bytes("synthetic-signature-purpose");
        byte[] id = bytes("bidsmart-test-user");
        byte[] signature = Sm2Signatures.sign(privateKey, id, message);
        assertTrue(Sm2Signatures.verify(publicKey, id, message, signature));
        assertFalse(Sm2Signatures.verify(publicKey, bytes("other-user"), message, signature));
        assertFalse(Sm2Signatures.verify(publicKey, id, bytes("tampered"), signature));
        assertFalse(Sm2Signatures.verify(publicKey, id, message, new byte[]{0}));
        assertThrows(IllegalArgumentException.class, () -> Sm2Signatures.sign(privateKey, new byte[0], message));
    }
    @Test void rejectsWrongEcDomain() {
        org.bouncycastle.asn1.x9.X9ECParameters curve = org.bouncycastle.asn1.sec.SECNamedCurves.getByName("secp256r1");
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(new ECDomainParameters(curve), new SecureRandom()));
        AsymmetricCipherKeyPair wrong = generator.generateKeyPair();
        assertThrows(IllegalArgumentException.class, () -> Sm2Signatures.sign((ECPrivateKeyParameters) wrong.getPrivate(), bytes("test-id"), bytes("test")));
    }
    private static AsymmetricCipherKeyPair pair(String name) {
        X9ECParameters curve = GMNamedCurves.getByName(name);
        ECKeyPairGenerator generator = new ECKeyPairGenerator();
        generator.init(new ECKeyGenerationParameters(new ECDomainParameters(curve), new SecureRandom()));
        return generator.generateKeyPair();
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
