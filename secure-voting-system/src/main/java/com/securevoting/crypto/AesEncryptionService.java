package com.securevoting.crypto;

import com.securevoting.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AesEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptionService.class);
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;
    private static final int KEY_LENGTH = 32; // 256 bits

    private final AppProperties props;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec keySpec;

    public AesEncryptionService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        String base64 = props.getCrypto().getAesKeyBase64();
        if (base64 == null || base64.isBlank() || base64.startsWith("REPLACE_WITH")) {
            throw new IllegalStateException(
                    "app.crypto.aes-key-base64 is not configured. Generate one with `openssl rand -base64 32`.");
        }
        byte[] key = Base64.getDecoder().decode(base64);
        if (key.length != KEY_LENGTH) {
            throw new IllegalStateException(
                    "AES key must be 32 bytes (256 bits) after Base64 decode; got " + key.length);
        }
        this.keySpec = new SecretKeySpec(key, "AES");
        log.info("AES-256-CBC encryption service initialised.");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ct, 0, combined, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("AES encryption failed", e);
        }
    }

    public String decrypt(String ciphertextBase64) {
        if (ciphertextBase64 == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertextBase64);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalStateException("Ciphertext too short");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] ct = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES decryption failed", e);
        }
    }
}