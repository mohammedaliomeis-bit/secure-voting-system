package com.securevoting.controller;

import com.securevoting.entity.Election;
import com.securevoting.entity.Invitation;
import com.securevoting.entity.User;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.UserRepository;
import com.securevoting.service.ElectionService;
import com.securevoting.service.InvitationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/elections/{code}/invitations")
public class InvitationController {

    private final ElectionService electionService;
    private final InvitationService invitationService;
    private final UserRepository userRepository;

    public InvitationController(ElectionService electionService,
                                InvitationService invitationService,
                                UserRepository userRepository) {
        this.electionService = electionService;
        this.invitationService = invitationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String list(@PathVariable String code,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        model.addAttribute("election", election);
        model.addAttribute("invitations", invitationService.listForElection(election));
        return "elections/invitations";
    }

    @PostMapping("/send-all")
    public String sendAll(@PathVariable String code,
                          @AuthenticationPrincipal UserDetails principal,
                          RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        try {
            int sent = invitationService.generateAndSendAll(election);
            ra.addFlashAttribute("info",
                    "Invitations sent: " + sent + ". Voters will receive their unique links by email.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/invitations";
    }

    @PostMapping("/{id}/resend")
    public String resend(@PathVariable String code,
                         @PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        Invitation inv = invitationService.requireById(id);
        if (!inv.getElection().getId().equals(election.getId())) {
            throw new AccessDeniedException("Invitation does not belong to this election.");
        }
        try {
            boolean ok = invitationService.resend(inv);
            ra.addFlashAttribute(ok ? "info" : "globalError",
                    ok ? "Invitation resent." : "Email delivery failed. Check mail server logs.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/invitations";
    }

    @PostMapping("/{id}/revoke")
    public String revoke(@PathVariable String code,
                         @PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        Invitation inv = invitationService.requireById(id);
        if (!inv.getElection().getId().equals(election.getId())) {
            throw new AccessDeniedException("Invitation does not belong to this election.");
        }
        invitationService.revoke(inv);
        ra.addFlashAttribute("info", "Invitation revoked.");
        return "redirect:/elections/" + code + "/invitations";
    }

    @PostMapping("/{id}/reissue")
    public String reissue(@PathVariable String code,
                          @PathVariable Long id,
                          @AuthenticationPrincipal UserDetails principal,
                          RedirectAttributes ra) {
        Election election = electionService.requireByCode(code);
        User owner = requireUser(principal);
        electionService.requireOwner(election, owner);
        Invitation inv = invitationService.requireById(id);
        if (!inv.getElection().getId().equals(election.getId())) {
            throw new AccessDeniedException("Invitation does not belong to this election.");
        }
        try {
            invitationService.reissue(inv);
            boolean ok = invitationService.resend(inv);
            ra.addFlashAttribute(ok ? "info" : "globalError",
                    ok ? "Invitation reissued and resent." : "Invitation reissued but email delivery failed.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("globalError", ex.getMessage());
        }
        return "redirect:/elections/" + code + "/invitations";
    }

    private User requireUser(UserDetails principal) {
        if (principal == null) throw new AccessDeniedException("Not signed in.");
        return userRepository.findByEmailHash(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }
}