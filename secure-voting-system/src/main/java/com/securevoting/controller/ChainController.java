package com.securevoting.controller;

import com.securevoting.entity.Block;
import com.securevoting.entity.Election;
import com.securevoting.entity.User;
import com.securevoting.entity.VoteAudit;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.ElectionRepository;
import com.securevoting.repository.InvitationRepository;
import com.securevoting.repository.VoteAuditRepository;
import com.securevoting.service.BlockchainService;
import com.securevoting.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
public class ChainController {

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final BlockchainService blockchainService;
    private final ElectionRepository electionRepository;
    private final VoteAuditRepository voteAuditRepository;
    private final InvitationRepository invitationRepository;
    private final UserService userService;

    public ChainController(BlockchainService blockchainService,
                           ElectionRepository electionRepository,
                           VoteAuditRepository voteAuditRepository,
                           InvitationRepository invitationRepository,
                           UserService userService) {
        this.blockchainService = blockchainService;
        this.electionRepository = electionRepository;
        this.voteAuditRepository = voteAuditRepository;
        this.invitationRepository = invitationRepository;
        this.userService = userService;
    }

    /** Public: the entire system chain. */
    @GetMapping("/chain")
    public String overview(Model model) {
        List<Block> chain = blockchainService.getAllBlocks();
        model.addAttribute("blocks", toRows(chain));
        model.addAttribute("report", blockchainService.validateChain());
        return "chain/overview";
    }

    /** Filtered: blocks belonging to one election. Owner or invited voter only. */
    @GetMapping("/elections/{code}/chain")
    public String electionChain(@PathVariable String code, Authentication auth, Model model) {
        Election election = electionRepository.findByElectionCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found: " + code));
        User user = userService.currentUser(auth);

        // Access: owner OR accepted invitation
        boolean isOwner = election.getCreator() != null
                && election.getCreator().getId().equals(user.getId());
        boolean isInvited = invitationRepository.hasAcceptedInvitation(election, user);
        if (!isOwner && !isInvited) {
            throw new ValidationException(
                    "You don't have access to this election's chain.");
        }

        // Find this election's block indices via the vote_audit table
        List<VoteAudit> audits = voteAuditRepository.findByElectionOrderByVotedAtAsc(election);
        Set<Long> wantedIndices = new HashSet<>(audits.size());
        for (VoteAudit a : audits) wantedIndices.add(a.getBlockIndex());

        // Filter the full chain to just this election's blocks (preserves chronological order)
        List<Block> electionBlocks = new ArrayList<>();
        for (Block b : blockchainService.getAllBlocks()) {
            if (wantedIndices.contains(b.getBlockIndex())) electionBlocks.add(b);
        }

        model.addAttribute("election", election);
        model.addAttribute("blocks", toRows(electionBlocks));
        model.addAttribute("report", blockchainService.validateChain());
        model.addAttribute("isOwner", isOwner);
        return "chain/election";
    }

    private List<BlockRow> toRows(List<Block> chain) {
        List<BlockRow> rows = new ArrayList<>(chain.size());
        for (Block b : chain) {
            rows.add(new BlockRow(
                    b.getBlockIndex(),
                    TS_FMT.format(Instant.ofEpochMilli(b.getTimestamp())),
                    b.getHash(),
                    b.getPrevHash(),
                    b.getNonce(),
                    b.getEncryptedData()
            ));
        }
        return rows;
    }

    public record BlockRow(
            long blockIndex,
            String timestamp,
            String hash,
            String prevHash,
            long nonce,
            String encryptedData
    ) {}
}