package com.securevoting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Crypto crypto = new Crypto();
    private final Uploads uploads = new Uploads();
    private final Otp otp = new Otp();
    private final Security security = new Security();
    private final Blockchain blockchain = new Blockchain();
    private final Rsa rsa = new Rsa();
    private final Mail mail = new Mail();

    /* ==================== Crypto ==================== */
    @Getter @Setter
    public static class Crypto {
        /** Base64-encoded 32-byte (256-bit) AES key. */
        private String aesKeyBase64;
    }

    /* ==================== Uploads ==================== */
    @Getter @Setter
    public static class Uploads {
        private String root = "./uploads";
        private String csvSubdir = "csv";
        private String candidatePhotoSubdir = "candidates";
    }

    /* ==================== OTP ==================== */
    @Getter @Setter
    public static class Otp {
        private int length = 6;
        private int ttlMinutes = 10;
        private int maxAttempts = 5;
    }

    /* ==================== Security ==================== */
    @Getter @Setter
    public static class Security {
        private final BruteForce bruteForce = new BruteForce();

        @Getter @Setter
        public static class BruteForce {
            private int maxFailedAttempts = 5;
            private int lockMinutes = 15;
        }
    }

    /* ==================== Blockchain (Phase 8) ==================== */
    @Getter @Setter
    public static class Blockchain {
        /** File path where the append-only ledger snapshot is written. */
        private String ledgerPath = "./blockchain-data/ledger.chain";
        /** Number of leading hex zeros required for a valid block hash (1..12). */
        private int difficulty = 4;
    }

    /* ==================== RSA (Phase 8) ==================== */
    @Getter @Setter
    public static class Rsa {
        /** Directory where the RSA keypair PEM files are stored. */
        private String keystorePath = "./blockchain-data/rsa-keys";
        /** Key size in bits. */
        private int keySize = 2048;
    }

    /* ==================== Mail ==================== */
    @Getter @Setter
    public static class Mail {
        /** "From" address on outgoing emails. */
        private String from;
        /** Display name on outgoing emails. */
        private String fromName;
    }
}