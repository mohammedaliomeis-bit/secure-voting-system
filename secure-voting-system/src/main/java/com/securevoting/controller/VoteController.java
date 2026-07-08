package com.securevoting.controller;

import com.securevoting.entity.Election;
import com.securevoting.entity.User;
import com.securevoting.entity.VoterRecord;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.UserRepository;
import com.securevoting.repository.VoterRecordRepository;
import com.securevoting.service.VoteService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@RequestMapping("/vote")
public class VoteController {

    private static final String SK_VR   = "VOTING_VR_ID";
    private static final String SK_AUTH = "VOTING_AUTHORIZED_VR_ID";

    private final VoteService voteService;
    private final VoterRecordRepository voterRepo;
    private final UserRepository userRepo;   // NEW: to resolve current user

    public VoteController(VoteService voteService,
                          VoterRecordRepository voterRepo,
                          UserRepository userRepo) {
        this.voteService = voteService;
        this.voterRepo = voterRepo;
        this.userRepo = userRepo;
    }

    /** Resolve the authenticated User entity from the Spring Security principal. */
    private User requireUser(UserDetails principal) {
        if (principal == null) {
            throw new ValidationException("You must be signed in to vote.");
        }
        return userRepo.findByEmailHash(principal.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Voter account not found."));
    }

    /* ----------- 1. IDENTIFY ----------- */

    @GetMapping("/{code}/identify")
    public String identifyForm(@PathVariable String code,
                               @AuthenticationPrincipal UserDetails principal,
                               Model model,
                               RedirectAttributes ra) {
        Election e = voteService.loadActiveElection(code);
        User user = requireUser(principal);

        // (NEW) Short-circuit: account already voted in this election.
        if (voteService.hasUserVoted(user, e)) {
            ra.addFlashAttribute("error",
                    "You have already voted in \"" + e.getTitle()
                            + "\". Each account may only vote once per election.");
            return "redirect:/dashboard";
        }

        model.addAttribute("election", e);
        model.addAttribute("fields", voteService.getIdentityFields(e));
        return "vote/identify";
    }

    @PostMapping("/{code}/identify")
    public String identifySubmit(@PathVariable String code,
                                 @RequestParam Map<String, String> params,
                                 @AuthenticationPrincipal UserDetails principal,
                                 HttpSession session,
                                 RedirectAttributes ra) {
        try {
            User user = requireUser(principal);
            VoterRecord vr = voteService.identifyVoter(code, params, user);
            voteService.issueVotingOtp(vr);
            session.setAttribute(SK_VR, vr.getId());
            return "redirect:/vote/" + code + "/verify-otp";
        } catch (ValidationException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/vote/" + code + "/identify";
        }
    }

    /* ----------- 2. VERIFY OTP ----------- */

    @GetMapping("/{code}/verify-otp")
    public String otpForm(@PathVariable String code, HttpSession session, Model model) {
        Long vrId = (Long) session.getAttribute(SK_VR);
        if (vrId == null) return "redirect:/vote/" + code + "/identify";
        VoterRecord vr = voterRepo.findById(vrId).orElse(null);
        if (vr == null) return "redirect:/vote/" + code + "/identify";
        model.addAttribute("election", vr.getElection());
        return "vote/verify-otp";
    }

    @PostMapping("/{code}/verify-otp")
    public String otpSubmit(@PathVariable String code,
                            @RequestParam String otp,
                            HttpSession session,
                            RedirectAttributes ra) {
        Long vrId = (Long) session.getAttribute(SK_VR);
        if (vrId == null) return "redirect:/vote/" + code + "/identify";
        VoterRecord vr = voterRepo.findById(vrId).orElse(null);
        if (vr == null) return "redirect:/vote/" + code + "/identify";
        try {
            voteService.verifyVotingOtp(vr, otp);
            session.removeAttribute(SK_VR);
            session.setAttribute(SK_AUTH, vr.getId());
            return "redirect:/vote/" + code + "/ballot";
        } catch (ValidationException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/vote/" + code + "/verify-otp";
        }
    }

    @PostMapping("/{code}/resend-otp")
    public String resendOtp(@PathVariable String code, HttpSession session, RedirectAttributes ra) {
        Long vrId = (Long) session.getAttribute(SK_VR);
        if (vrId == null) return "redirect:/vote/" + code + "/identify";
        VoterRecord vr = voterRepo.findById(vrId).orElse(null);
        if (vr == null) return "redirect:/vote/" + code + "/identify";
        try {
            voteService.issueVotingOtp(vr);
            ra.addFlashAttribute("info", "A new code has been sent to your registered email.");
        } catch (ValidationException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/vote/" + code + "/verify-otp";
    }

    /* ----------- 3. BALLOT ----------- */

    @GetMapping("/{code}/ballot")
    public String ballot(@PathVariable String code, HttpSession session, Model model) {
        Long vrId = (Long) session.getAttribute(SK_AUTH);
        if (vrId == null) return "redirect:/vote/" + code + "/identify";
        VoterRecord vr = voterRepo.findById(vrId).orElse(null);
        if (vr == null || vr.isVoted()) return "redirect:/vote/" + code + "/identify";
        model.addAttribute("election", vr.getElection());
        model.addAttribute("candidates", voteService.getCandidates(vr.getElection()));
        return "vote/ballot";
    }

    @PostMapping("/{code}/ballot")
    public String castBallot(@PathVariable String code,
                             @RequestParam Long candidateId,
                             @AuthenticationPrincipal UserDetails principal,
                             HttpSession session,
                             RedirectAttributes ra) {
        Long vrId = (Long) session.getAttribute(SK_AUTH);
        if (vrId == null) return "redirect:/vote/" + code + "/identify";
        VoterRecord vr = voterRepo.findById(vrId).orElse(null);
        if (vr == null) return "redirect:/vote/" + code + "/identify";
        try {
            User user = requireUser(principal);
            VoteService.VoteReceipt receipt = voteService.castVote(vr, candidateId, user);
            session.removeAttribute(SK_AUTH);
            ra.addFlashAttribute("receipt", receipt);
            return "redirect:/vote/" + code + "/receipt";
        } catch (ValidationException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/vote/" + code + "/ballot";
        }
    }

    /* ----------- 4. RECEIPT (one-time) ----------- */

    @GetMapping("/{code}/receipt")
    public String receipt(@PathVariable String code, Model model) {
        if (!model.containsAttribute("receipt")) {
            return "redirect:/dashboard";
        }
        return "vote/receipt";
    }
}