package com.securevoting.security;

import com.securevoting.config.AppProperties;
import com.securevoting.entity.User;
import com.securevoting.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class BruteForceProtectionService {

    private final AppProperties props;
    private final UserRepository userRepository;
    private final SecurityAuditLogger audit;

    public BruteForceProtectionService(AppProperties props,
                                       UserRepository userRepository,
                                       SecurityAuditLogger audit) {
        this.props = props;
        this.userRepository = userRepository;
        this.audit = audit;
    }

    public boolean isLocked(User user) {
        Instant until = user.getLockedUntil();
        if (until == null) return false;
        if (Instant.now().isAfter(until)) {
            // lock expired — reset
            user.setLockedUntil(null);
            user.setFailedAttempts(0);
            userRepository.save(user);
            return false;
        }
        return true;
    }

    @Transactional
    public void recordSuccess(User user) {
        if (user.getFailedAttempts() != 0 || user.getLockedUntil() != null) {
            user.setFailedAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    @Transactional
    public void recordFailure(User user, String remoteIp) {
        int max = props.getSecurity().getBruteForce().getMaxFailedAttempts();
        int lockMinutes = props.getSecurity().getBruteForce().getLockMinutes();
        int next = user.getFailedAttempts() + 1;
        user.setFailedAttempts(next);
        if (next >= max) {
            user.setLockedUntil(Instant.now().plus(lockMinutes, ChronoUnit.MINUTES));
            audit.accountLocked(user.getEmailHash(), remoteIp);
        }
        userRepository.save(user);
    }
}