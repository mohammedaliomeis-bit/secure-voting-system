package com.securevoting.security;

import com.securevoting.crypto.HashUtil;
import com.securevoting.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class AuthFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final UserRepository userRepository;
    private final BruteForceProtectionService bruteForce;
    private final SecurityAuditLogger audit;

    public AuthFailureHandler(UserRepository userRepository,
                              BruteForceProtectionService bruteForce,
                              SecurityAuditLogger audit) {
        this.userRepository = userRepository;
        this.bruteForce = bruteForce;
        this.audit = audit;
        setDefaultFailureUrl("/login?error");
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException, ServletException {
        // SecurityConfig sets usernameParameter("email") — read the same field here.
        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            String emailHash = HashUtil.emailLookupHash(email);
            userRepository.findByEmailHash(emailHash).ifPresent(u ->
                    bruteForce.recordFailure(u, request.getRemoteAddr()));
            audit.loginFailure(emailHash, request.getRemoteAddr(),
                    exception.getClass().getSimpleName());
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}