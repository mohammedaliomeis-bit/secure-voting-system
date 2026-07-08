package com.securevoting.security;

import com.securevoting.crypto.HashUtil;
import com.securevoting.entity.User;
import com.securevoting.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final BruteForceProtectionService bruteForce;

    public CustomUserDetailsService(UserRepository userRepository,
                                    BruteForceProtectionService bruteForce) {
        this.userRepository = userRepository;
        this.bruteForce = bruteForce;
    }

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String emailHash = HashUtil.emailLookupHash(email);
        User user = userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));

        boolean locked = bruteForce.isLocked(user);

        return org.springframework.security.core.userdetails.User.builder()
                .username(emailHash)                          // username = email hash; we don't expose plaintext to the principal
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .accountLocked(locked)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}