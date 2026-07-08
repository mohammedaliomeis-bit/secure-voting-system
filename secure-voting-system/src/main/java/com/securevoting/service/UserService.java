package com.securevoting.service;

import com.securevoting.crypto.AesEncryptionService;
import com.securevoting.crypto.HashUtil;
import com.securevoting.entity.User;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final AesEncryptionService aes;

    public UserService(UserRepository userRepository, AesEncryptionService aes) {
        this.userRepository = userRepository;
        this.aes = aes;
    }

    @Transactional(readOnly = true)
    public User currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResourceNotFoundException("Not authenticated");
        }
        String emailHash = authentication.getName();
        return userRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }


    public String decryptedFullName(User user) {
        return aes.decrypt(user.getFirstNameEncrypted()) + " " + aes.decrypt(user.getLastNameEncrypted());
    }

    public String decryptedEmail(User user) {
        return aes.decrypt(user.getEmailEncrypted());
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmailHash(HashUtil.emailLookupHash(email));
    }
    /**
     * Loads the user by the Spring Security principal name, which in this app is the email hash.
     * Used by controllers that need the authenticated User entity from Spring Security's Authentication.
     */
    @Transactional(readOnly = true)
    public User requireByEmail(String principalEmailHash) {
        return userRepository.findByEmailHash(principalEmailHash)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found for principal: " + principalEmailHash));
    }
}