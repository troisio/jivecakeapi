package com.jivecake.api.service;

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class CryptoService {
    public static boolean isValidSHA1(String body, String secret, String signature) throws NoSuchAlgorithmException, InvalidKeyException {
        String hash = signature.substring(5);
        Mac hmac = Mac.getInstance("HmacSHA1");
        hmac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA1"));

        byte[] bytes = hmac.doFinal(body.getBytes(Charset.forName("UTF-8")));
        char[] hexCharacters = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        char[] characters = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int v = bytes[index] & 0xFF;
            characters[index * 2] = hexCharacters[v >>> 4];
            characters[index * 2 + 1] = hexCharacters[v & 0x0F];
        }

        return hash.equals(new String(characters));
    }
}