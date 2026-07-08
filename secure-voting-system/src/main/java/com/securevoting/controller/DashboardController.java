package com.securevoting.controller;

import com.securevoting.entity.Election;
import com.securevoting.entity.ElectionParticipation;
import com.securevoting.entity.ElectionStatus;
import com.securevoting.entity.Invitation;
import com.securevoting.entity.User;
import com.securevoting.repository.ElectionParticipationRepository;
import com.securevoting.repository.ElectionRepository;
import com.securevoting.repository.InvitationRepository;
import com.securevoting.service.BlockchainService;
import com.securevoting.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class DashboardController {

    private final UserService userService;
    private final ElectionRepository electionRepo;
    private final ElectionParticipationRepository partRepo;
    private final InvitationRepository invitationRepo;
    private final BlockchainService blockchainService;

    public DashboardController(UserService userService,
                               ElectionRepository electionRepo,
                               ElectionParticipationRepository partRepo,
                               InvitationRepository invitationRepo,
                               BlockchainService blockchainService) {
        this.userService = userService;
        this.electionRepo = electionRepo;
        this.partRepo = partRepo;
        this.invitationRepo = invitationRepo;
        this.blockchainService = blockchainService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication auth, Model model) {
        User user = userService.currentUser(auth);

        // ---- Greeting ----
        String fullName = userService.decryptedFullName(user);
        String firstName = (fullName != null && !fullName.isBlank())
                ? fullName.trim().split("\\s+")[0]
                : "";
        model.addAttribute("firstName", firstName);

        // ---- Your invitations (the only way an election reaches a voter's dashboard) ----
        List<Invitation> acceptedInvitations = invitationRepo.findAcceptedByUser(user);
        List<InvitedElectionRow> invitedRows = new ArrayList<>();
        Set<Long> seenElectionIds = new HashSet<>();
        int actionableInvitations = 0;
        for (Invitation inv : acceptedInvitations) {
            Election e = inv.getElection();
            if (e == null) continue;
            if (!seenElectionIds.add(e.getId())) continue; // dedupe by election
            boolean alreadyVoted = partRepo.existsByUserAndElection(user, e);
            invitedRows.add(new InvitedElectionRow(
                    e.getElectionCode(),
                    e.getTitle(),
                    e.getDescription(),
                    e.getStatus(),
                    e.getStartTime(),
                    e.getEndTime(),
                    alreadyVoted
            ));
            if ((e.getStatus() == ElectionStatus.ACTIVE && !alreadyVoted)
                    || e.getStatus() == ElectionStatus.SCHEDULED) {
                actionableInvitations++;
            }
        }
        model.addAttribute("invitedElections", invitedRows);
        model.addAttribute("invitedCount", actionableInvitations);

        // ---- Elections this user organizes ----
        List<Election> myElections = electionRepo.findByCreatorOrderByCreatedAtDesc(user);
        model.addAttribute("myElections", myElections);
        model.addAttribute("createdCount", myElections.size());
        int activeCount = 0;
        for (Election e : myElections) {
            if (e.getStatus() == ElectionStatus.ACTIVE) activeCount++;
        }
        model.addAttribute("activeCount", activeCount);

        // ---- Elections this user voted in ----
        List<ElectionParticipation> parts = partRepo.findByUserOrderByVotedAtDesc(user);
        List<VotedRow> votedRows = new ArrayList<>(parts.size());
        for (ElectionParticipation p : parts) {
            Election e = p.getElection();
            if (e == null) continue;
            votedRows.add(new VotedRow(
                    e.getElectionCode(),
                    e.getTitle(),
                    e.getStatus(),
                    p.getVotedAt()
            ));
        }
        model.addAttribute("votedElections", votedRows);

        // ---- Chain stats ----
        model.addAttribute("chainBlocks", blockchainService.size());

        return "dashboard/index";
    }

    public record InvitedElectionRow(
            String electionCode,
            String title,
            String description,
            ElectionStatus status,
            Instant startTime,
            Instant endTime,
            boolean alreadyVoted
    ) {}

    public record VotedRow(
            String electionCode,
            String title,
            ElectionStatus status,
            Instant votedAt
    ) {}
}