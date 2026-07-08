package com.securevoting.security;

import com.securevoting.entity.User;
import com.securevoting.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final BruteForceProtectionService bruteForce;
    private final SecurityAuditLogger audit;

    public AuthSuccessHandler(UserRepository userRepository,
                              BruteForceProtectionService bruteForce,
                              SecurityAuditLogger audit) {
        this.userRepository = userRepository;
        this.bruteForce = bruteForce;
        this.audit = audit;
        setDefaultTargetUrl("/dashboard");
        setAlwaysUseDefaultTargetUrl(false);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {
        String emailHash = authentication.getName();
        userRepository.findByEmailHash(emailHash).ifPresent((User u) -> {
            bruteForce.recordSuccess(u);
            audit.loginSuccess(emailHash, request.getRemoteAddr());
        });
        super.onAuthenticationSuccess(request, response, authentication);
    }
}