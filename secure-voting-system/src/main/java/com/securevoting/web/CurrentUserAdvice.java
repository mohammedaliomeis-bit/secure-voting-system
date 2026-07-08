package com.securevoting.web;

import com.securevoting.entity.User;
import com.securevoting.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Populates every view with the current signed-in user's decrypted display name,
 * email, and initial. The Spring Security principal name in this app is the
 * email *hash*, which is not friendly to display directly in the UI.
 */
@ControllerAdvice
public class CurrentUserAdvice {

    private final UserService userService;

    public CurrentUserAdvice(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute
    public void addCurrentUser(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            return;
        }
        try {
            User user = userService.currentUser(auth);
            String fullName = userService.decryptedFullName(user);
            String email    = userService.decryptedEmail(user);

            if (fullName == null || fullName.isBlank()) fullName = email;
            String initial = (fullName != null && !fullName.isBlank())
                    ? fullName.substring(0, 1).toUpperCase()
                    : "U";

            model.addAttribute("currentUserName",    fullName);
            model.addAttribute("currentUserEmail",   email);
            model.addAttribute("currentUserInitial", initial);
        } catch (Exception ignored) {
            // Stay silent — navbar will fall back to its default text.
        }
    }
}