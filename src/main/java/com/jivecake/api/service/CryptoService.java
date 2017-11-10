package com.jivecake.api.service;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.util.encoders.Hex;

public class CryptoService {
    public static boolean isValidSHA1(String body, String secret, String signature) throws NoSuchAlgorithmException, InvalidKeyException {
        String hash = signature.substring(5);
        Mac hmac = Mac.getInstance("HmacSHA1");
        hmac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA1"));
        String calculatedSignature = Hex.toHexString(
            hmac.doFinal(body.getBytes(Charset.forName("UTF-8")))
        );
        return hash.equals(calculatedSignature);
    }
}
