package com.securevoting.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger("SECURITY_AUDIT");

    public void loginSuccess(String emailHash, String remoteIp) {
        log.info("event=login_success emailHash={} ip={}", emailHash, remoteIp);
    }

    public void loginFailure(String emailHash, String remoteIp, String reason) {
        log.warn("event=login_failure emailHash={} ip={} reason={}", emailHash, remoteIp, reason);
    }

    public void accountLocked(String emailHash, String remoteIp) {
        log.warn("event=account_locked emailHash={} ip={}", emailHash, remoteIp);
    }

    public void registration(String emailHash, String remoteIp) {
        log.info("event=registration emailHash={} ip={}", emailHash, remoteIp);
    }

    public void otpIssued(String emailHash, String purpose) {
        log.info("event=otp_issued emailHash={} purpose={}", emailHash, purpose);
    }

    public void otpVerified(String emailHash, String purpose) {
        log.info("event=otp_verified emailHash={} purpose={}", emailHash, purpose);
    }
}