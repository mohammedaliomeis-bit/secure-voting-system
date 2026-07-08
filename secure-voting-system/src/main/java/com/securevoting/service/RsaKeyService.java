package com.securevoting.service;

import com.securevoting.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class RsaKeyService {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyService.class);
    private static final String PUBLIC_FILE = "public.pem";
    private static final String PRIVATE_FILE = "private.pem";
    private static final String RSA_CIPHER = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";

    private final Path keystoreDir;
    private final int keySize;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    public RsaKeyService(AppProperties props) {
        this.keystoreDir = Paths.get(props.getRsa().getKeystorePath());
        this.keySize = props.getRsa().getKeySize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(keystoreDir);
            Path pub = keystoreDir.resolve(PUBLIC_FILE);
            Path priv = keystoreDir.resolve(PRIVATE_FILE);

            if (Files.exists(pub) && Files.exists(priv)) {
                this.publicKey = loadPublic(pub);
                this.privateKey = loadPrivate(priv);
                log.info("RsaKeyService loaded existing keypair from {} ({}-bit).", keystoreDir, keySize);
            } else {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(keySize, new SecureRandom());
                KeyPair kp = gen.generateKeyPair();
                this.publicKey = kp.getPublic();
                this.privateKey = kp.getPrivate();
                writePem(pub, "PUBLIC KEY", publicKey.getEncoded());
                writePem(priv, "PRIVATE KEY", privateKey.getEncoded());
                log.info("RsaKeyService generated new {}-bit keypair at {}.", keySize, keystoreDir);
            }

            // Sanity round-trip
            String test = "rsa-warmup-" + System.currentTimeMillis();
            if (!test.equals(decryptToString(encryptFromString(test)))) {
                throw new IllegalStateException("RSA warm-up failed.");
            }
            log.info("RsaKeyService ready (RSA-{}/OAEP).", keySize);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize RsaKeyService", ex);
        }
    }

    /* ==================== Public API ==================== */

    public PublicKey getPublicKey()   { return publicKey; }
    public PrivateKey getPrivateKey() { return privateKey; }

    public String encryptFromString(String plaintext) {
        try {
            Cipher c = Cipher.getInstance(RSA_CIPHER);
            c.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] out = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("RSA encryption failed", ex);
        }
    }

    public String decryptToString(String base64Ciphertext) {
        try {
            Cipher c = Cipher.getInstance(RSA_CIPHER);
            c.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] plain = c.doFinal(Base64.getDecoder().decode(base64Ciphertext));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("RSA decryption failed", ex);
        }
    }

    /* ==================== PEM helpers ==================== */

    private void writePem(Path path, String label, byte[] der) throws IOException {
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8))
                .encodeToString(der);
        String pem = "-----BEGIN " + label + "-----\n" + b64 + "\n-----END " + label + "-----\n";
        Files.writeString(path, pem, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private PublicKey loadPublic(Path path) throws Exception {
        byte[] der = pemToDer(Files.readString(path, StandardCharsets.UTF_8), "PUBLIC KEY");
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private PrivateKey loadPrivate(Path path) throws Exception {
        byte[] der = pemToDer(Files.readString(path, StandardCharsets.UTF_8), "PRIVATE KEY");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private byte[] pemToDer(String pem, String label) {
        String stripped = pem
                .replace("-----BEGIN " + label + "-----", "")
                .replace("-----END " + label + "-----", "")
                .replaceAll("\\s+", "");
        return Base64.getDecoder().decode(stripped);
    }
}