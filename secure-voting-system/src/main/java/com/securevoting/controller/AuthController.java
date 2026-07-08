package com.securevoting.controller;

import com.securevoting.dto.OtpRequest;
import com.securevoting.dto.RegisterRequest;
import com.securevoting.exception.ValidationException;
import com.securevoting.service.AuthService;
import com.securevoting.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private static final String PENDING_USER_ID = "PENDING_USER_ID";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // -------- Register --------

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterRequest());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String submitRegister(@Valid @ModelAttribute("form") RegisterRequest form,
                                 BindingResult br,
                                 HttpServletRequest request,
                                 HttpSession session,
                                 Model model) {
        if (br.hasErrors()) return "auth/register";
        try {
            Long userId = authService.register(form, request.getRemoteAddr());
            session.setAttribute(PENDING_USER_ID, userId);
        } catch (ValidationException ex) {
            br.rejectValue("email", "duplicate", ex.getMessage());
            return "auth/register";
        } catch (IllegalStateException ex) {
            model.addAttribute("globalError", ex.getMessage());
            return "auth/register";
        }
        return "redirect:/verify-otp";
    }

    // -------- Verify OTP --------

    @GetMapping("/verify-otp")
    public String otpForm(HttpSession session, Model model) {
        Long pending = (Long) session.getAttribute(PENDING_USER_ID);
        if (pending == null) return "redirect:/register";
        if (!model.containsAttribute("form")) model.addAttribute("form", new OtpRequest());
        return "auth/verify-otp";
    }

    @PostMapping("/verify-otp")
    public String submitOtp(@Valid @ModelAttribute("form") OtpRequest form,
                            BindingResult br,
                            HttpSession session,
                            RedirectAttributes flash,
                            Model model) {
        Long pending = (Long) session.getAttribute(PENDING_USER_ID);
        if (pending == null) return "redirect:/register";
        if (br.hasErrors()) return "auth/verify-otp";

        OtpService.Result r = authService.verifyRegistrationOtp(pending, form.getCode());
        switch (r) {
            case OK -> {
                session.removeAttribute(PENDING_USER_ID);
                flash.addAttribute("verified", "");
                return "redirect:/login?verified";
            }
            case INVALID_CODE -> br.rejectValue("code", "invalid", "Invalid code. Please try again.");
            case EXPIRED -> {
                model.addAttribute("globalError", "Code expired. Click resend for a new one.");
            }
            case TOO_MANY_ATTEMPTS -> {
                model.addAttribute("globalError", "Too many attempts. Click resend for a new code.");
            }
            case NO_PENDING_OTP -> {
                model.addAttribute("globalError", "No active code. Click resend.");
            }
        }
        return "auth/verify-otp";
    }

    @PostMapping("/resend-otp")
    public String resend(HttpSession session, RedirectAttributes flash) {
        Long pending = (Long) session.getAttribute(PENDING_USER_ID);
        if (pending == null) return "redirect:/register";
        try {
            authService.resendRegistrationOtp(pending);
            flash.addFlashAttribute("info", "A new code has been sent to your email.");
        } catch (IllegalStateException ex) {
            flash.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/verify-otp";
    }

    // -------- Login --------

    @GetMapping("/login")
    public String loginForm() {
        return "auth/login";
    }
}