package com.securevoting.service;

import com.securevoting.crypto.AesEncryptionService;
import com.securevoting.crypto.HashUtil;
import com.securevoting.dto.RegisterRequest;
import com.securevoting.entity.User;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.UserRepository;
import com.securevoting.security.SecurityAuditLogger;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AesEncryptionService aes;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditLogger audit;
    private final OtpService otpService;

    public AuthService(UserRepository userRepository,
                       AesEncryptionService aes,
                       PasswordEncoder passwordEncoder,
                       SecurityAuditLogger audit,
                       OtpService otpService) {
        this.userRepository = userRepository;
        this.aes = aes;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.otpService = otpService;
    }

    /** Returns the new user's id so the controller can stash it in the session for OTP step. */
    @Transactional
    public Long register(RegisterRequest req, String remoteIp) {
        String email = req.getEmail().trim().toLowerCase();
        String emailHash = HashUtil.emailLookupHash(email);
        if (userRepository.existsByEmailHash(emailHash)) {
            throw new ValidationException("An account with this email already exists.");
        }

        User u = new User();
        u.setFirstNameEncrypted(aes.encrypt(req.getFirstName().trim()));
        u.setLastNameEncrypted(aes.encrypt(req.getLastName().trim()));
        u.setEmailHash(emailHash);
        u.setEmailEncrypted(aes.encrypt(email));
        u.setDateOfBirth(req.getDateOfBirth());
        u.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        u.setEnabled(false);                       // OTP-gated activation
        u.setFailedAttempts(0);
        userRepository.save(u);
        audit.registration(emailHash, remoteIp);

        // Issue and email the OTP
        otpService.issueRegistrationOtp(u, email, req.getFirstName().trim());
        return u.getId();
    }

    @Transactional
    public void resendRegistrationOtp(Long userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pending user not found."));
        if (u.isEnabled()) return; // already verified, nothing to do
        String email = aes.decrypt(u.getEmailEncrypted());
        String firstName = aes.decrypt(u.getFirstNameEncrypted());
        otpService.issueRegistrationOtp(u, email, firstName);
    }

    @Transactional
    public OtpService.Result verifyRegistrationOtp(Long userId, String code) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pending user not found."));
        if (u.isEnabled()) return OtpService.Result.OK;
        OtpService.Result r = otpService.verifyRegistrationOtp(u, code);
        if (r == OtpService.Result.OK) {
            u.setEnabled(true);
            userRepository.save(u);
        }
        return r;
    }
}