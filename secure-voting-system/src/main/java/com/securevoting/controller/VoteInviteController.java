package com.securevoting.controller;

import com.securevoting.entity.Invitation;
import com.securevoting.entity.User;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.UserRepository;
import com.securevoting.service.InvitationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/vote/invite")
public class VoteInviteController {

    private final InvitationService invitationService;
    private final UserRepository userRepository;

    public VoteInviteController(InvitationService invitationService,
                                UserRepository userRepository) {
        this.invitationService = invitationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{token}")
    public String accept(@PathVariable String token,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        // Spring Security guarantees principal != null here (URL is authenticated())
        User user = userRepository.findByEmailHash(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
        try {
            Invitation inv = invitationService.acceptInvitation(token, user);
            ra.addFlashAttribute("info",
                    "You're invited to vote in: \"" + inv.getElection().getTitle()
                            + "\". It's now on your dashboard.");
            return "redirect:/dashboard#invitations";
        } catch (ResourceNotFoundException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
            return "redirect:/dashboard";
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
            return "redirect:/dashboard";
        } catch (AccessDeniedException ex) {
            ra.addFlashAttribute("globalError", "Access denied.");
            return "redirect:/dashboard";
        }
    }
}