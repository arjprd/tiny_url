package com.example.tinyurl.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AESUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";

    /**
     * Encrypts a value using AES-256
     * @param value The value to encrypt
     * @param secretKey The secret key (must be 32 bytes for AES-256)
     * @return Base64 encoded encrypted string
     */
    public static String encrypt(String value, String secretKey) {
        try {
            // Ensure key is exactly 32 bytes for AES-256
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length != 32) {
                // Pad or truncate to 32 bytes
                byte[] paddedKey = new byte[32];
                System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
                keyBytes = paddedKey;
            }

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);

            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting value", e);
        }
    }

    /**
     * Decrypts a Base64 encoded encrypted value using AES-256
     * @param encryptedValue The Base64 encoded encrypted value
     * @param secretKey The secret key (must be 32 bytes for AES-256)
     * @return Decrypted string
     */
    public static String decrypt(String encryptedValue, String secretKey) {
        try {
            // Ensure key is exactly 32 bytes for AES-256
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length != 32) {
                // Pad or truncate to 32 bytes
                byte[] paddedKey = new byte[32];
                System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
                keyBytes = paddedKey;
            }

            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);

            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedValue));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting value", e);
        }
    }
}

