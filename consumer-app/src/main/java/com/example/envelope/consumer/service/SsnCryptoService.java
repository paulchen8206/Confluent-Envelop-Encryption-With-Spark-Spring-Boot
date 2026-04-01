package com.example.envelope.consumer.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SsnCryptoService {

    private static final String PREFIX = "enc:v1:";

    private final SecretKeySpec key;

    public SsnCryptoService(@Value("${app.field-encryption.secret:demo-field-secret-change-me}") String secret) {
        this.key = new SecretKeySpec(hash(secret), "AES");
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank() || !encrypted.startsWith(PREFIX)) {
            return encrypted;
        }
        try {
            String[] parts = encrypted.substring(PREFIX.length()).split(":", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt ssn field", e);
        }
    }

    private byte[] hash(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize decryption key", e);
        }
    }
}
