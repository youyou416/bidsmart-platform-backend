package com.bidsmart.platform.crypto;

import java.security.SecureRandom;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithID;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.SM2Signer;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.params.ECKeyParameters;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;

/** Controlled extension only: SM3withSM2, caller-supplied user ID, ASN.1 DER signature bytes. */
public final class Sm2Signatures {
    private Sm2Signatures() { }
    public static byte[] sign(ECPrivateKeyParameters key, byte[] userId, byte[] message) {
        validate(key, userId, message);
        SM2Signer signer = new SM2Signer(new SM3Digest());
        signer.init(true, new ParametersWithID(new ParametersWithRandom(key, new SecureRandom()), userId.clone()));
        signer.update(message, 0, message.length);
        try { return signer.generateSignature(); }
        catch (org.bouncycastle.crypto.CryptoException failure) { throw new IllegalStateException("SM2 signing failed", failure); }
    }
    public static boolean verify(ECPublicKeyParameters key, byte[] userId, byte[] message, byte[] signature) {
        validate(key, userId, message);
        if (signature == null) { return false; }
        SM2Signer signer = new SM2Signer(new SM3Digest());
        signer.init(false, new ParametersWithID(key, userId.clone()));
        signer.update(message, 0, message.length);
        return signer.verifySignature(signature);
    }
    private static void validate(CipherParameters key, byte[] userId, byte[] message) {
        if (key == null || userId == null || userId.length == 0 || userId.length > 8191 || message == null) {
            throw new IllegalArgumentException("SM2 requires a key, explicit user ID of 1..8191 bytes, and message");
        }
        X9ECParameters expected = GMNamedCurves.getByName("sm2p256v1");
        ECKeyParameters ecKey = (ECKeyParameters) key;
        if (!expected.getCurve().equals(ecKey.getParameters().getCurve())
                || !expected.getN().equals(ecKey.getParameters().getN())
                || !expected.getG().equals(ecKey.getParameters().getG())) {
            throw new IllegalArgumentException("SM2 requires sm2p256v1 domain parameters");
        }
    }
}
