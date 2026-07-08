package com.securevoting.service;

import com.securevoting.config.AppProperties;
import com.securevoting.crypto.AesEncryptionService;
import com.securevoting.crypto.HashUtil;
import com.securevoting.entity.OtpPurpose;
import com.securevoting.entity.OtpToken;
import com.securevoting.entity.User;
import com.securevoting.repository.OtpTokenRepository;
import com.securevoting.security.SecurityAuditLogger;
import com.securevoting.util.CodeGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final long MIN_RESEND_SECONDS = 60;

    private final OtpTokenRepository otpRepo;
    private final AppProperties props;
    private final AesEncryptionService aes;
    private final MailService mailService;
    private final SecurityAuditLogger audit;

    public OtpService(OtpTokenRepository otpRepo,
                      AppProperties props,
                      AesEncryptionService aes,
                      MailService mailService,
                      SecurityAuditLogger audit) {
        this.otpRepo = otpRepo;
        this.props = props;
        this.aes = aes;
        this.mailService = mailService;
        this.audit = audit;
    }

    public enum Result { OK, INVALID_CODE, EXPIRED, TOO_MANY_ATTEMPTS, NO_PENDING_OTP }

    /** Issue an OTP for registration and email it. Throttled. */
    @Transactional
    public void issueRegistrationOtp(User user, String plaintextEmail, String displayName) {
        Optional<OtpToken> active = otpRepo
                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(user, OtpPurpose.REGISTRATION);

        if (active.isPresent()) {
            long ageSec = ChronoUnit.SECONDS.between(active.get().getCreatedAt(), Instant.now());
            if (ageSec < MIN_RESEND_SECONDS) {
                throw new IllegalStateException(
                        "Please wait " + (MIN_RESEND_SECONDS - ageSec) + " seconds before requesting another code.");
            }
            // mark previous as used so only one active OTP exists at a time
            active.get().setUsed(true);
            otpRepo.save(active.get());
        }

        int length = props.getOtp().getLength();
        int ttl = props.getOtp().getTtlMinutes();
        String code = CodeGenerator.numericOtp(length);

        OtpToken token = new OtpToken();
        token.setUser(user);
        token.setPurpose(OtpPurpose.REGISTRATION);
        token.setCodeHash(HashUtil.sha256Hex(code));
        token.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.MINUTES));
        token.setDestinationEmailEncrypted(aes.encrypt(plaintextEmail));
        otpRepo.save(token);

        mailService.sendRegistrationOtp(plaintextEmail, displayName, code, ttl);
        audit.otpIssued(user.getEmailHash(), OtpPurpose.REGISTRATION.name());
    }

    /** Verify a registration OTP. Mutates the token (attempts++, used=true on success). */
    @Transactional
    public Result verifyRegistrationOtp(User user, String submittedCode) {
        Optional<OtpToken> opt = otpRepo
                .findTopByUserAndPurposeAndUsedFalseOrderByCreatedAtDesc(user, OtpPurpose.REGISTRATION);

        if (opt.isEmpty()) return Result.NO_PENDING_OTP;
        OtpToken token = opt.get();

        if (Instant.now().isAfter(token.getExpiresAt())) {
            token.setUsed(true);
            otpRepo.save(token);
            return Result.EXPIRED;
        }
        if (token.getAttempts() >= props.getOtp().getMaxAttempts()) {
            token.setUsed(true);
            otpRepo.save(token);
            return Result.TOO_MANY_ATTEMPTS;
        }

        token.setAttempts(token.getAttempts() + 1);

        String submittedHash = HashUtil.sha256Hex(submittedCode == null ? "" : submittedCode.trim());
        if (!constantTimeEquals(submittedHash, token.getCodeHash())) {
            otpRepo.save(token);
            return Result.INVALID_CODE;
        }

        token.setUsed(true);
        otpRepo.save(token);
        audit.otpVerified(user.getEmailHash(), OtpPurpose.REGISTRATION.name());
        return Result.OK;
    }

    /** Hourly sweep of expired tokens. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpired() {
        int n = otpRepo.deleteExpired(Instant.now().minus(1, ChronoUnit.HOURS));
        if (n > 0) log.info("Purged {} expired OTP tokens", n);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}