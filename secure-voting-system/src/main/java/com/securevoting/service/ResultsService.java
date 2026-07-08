package com.securevoting.service;

import com.securevoting.dto.CandidateResult;
import com.securevoting.dto.ElectionResults;
import com.securevoting.entity.Block;
import com.securevoting.entity.Candidate;
import com.securevoting.entity.Election;
import com.securevoting.repository.CandidateRepository;
import com.securevoting.repository.VoterRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Computes election results from the blockchain. Authorization (organizer vs.
 * voter-of-closed-election) is enforced by the caller (ResultsController);
 * this service only does the math.
 */
@Service
public class ResultsService {

    private static final Logger log = LoggerFactory.getLogger(ResultsService.class);

    private static final Pattern PAYLOAD_RX = Pattern.compile(
            "\"electionCode\"\\s*:\\s*\"([^\"]+)\"[^}]*?\"candidateId\"\\s*:\\s*(\\d+)"
    );

    private final ElectionService electionService;
    private final CandidateRepository candidateRepo;
    private final VoterRecordRepository voterRepo;
    private final BlockchainService blockchainService;
    private final RsaKeyService rsaKeyService;

    public ResultsService(ElectionService electionService,
                          CandidateRepository candidateRepo,
                          VoterRecordRepository voterRepo,
                          BlockchainService blockchainService,
                          RsaKeyService rsaKeyService) {
        this.electionService = electionService;
        this.candidateRepo = candidateRepo;
        this.voterRepo = voterRepo;
        this.blockchainService = blockchainService;
        this.rsaKeyService = rsaKeyService;
    }

    @Transactional(readOnly = true)
    public ElectionResults compute(String code) {
        Election election = electionService.requireByCode(code);

        List<Candidate> candidates = candidateRepo.findByElectionOrderByDisplayOrderAsc(election);
        Map<Long, Integer> tallies = new LinkedHashMap<>();
        for (Candidate c : candidates) tallies.put(c.getId(), 0);

        int decryptionFailures = 0;
        int totalVotes = 0;

        List<Block> blocks = blockchainService.getAllBlocks();
        for (Block block : blocks) {
            String enc = block.getEncryptedData();
            if (enc == null || "GENESIS".equals(enc)) continue;

            String payload;
            try {
                payload = rsaKeyService.decryptToString(enc);
            } catch (Exception e) {
                decryptionFailures++;
                continue;
            }
            if (payload == null) {
                decryptionFailures++;
                continue;
            }

            Matcher m = PAYLOAD_RX.matcher(payload);
            if (!m.find()) continue;

            String voteElectionCode = m.group(1);
            if (!election.getElectionCode().equals(voteElectionCode)) continue;

            long candidateId;
            try {
                candidateId = Long.parseLong(m.group(2));
            } catch (NumberFormatException ex) {
                continue;
            }

            if (tallies.containsKey(candidateId)) {
                tallies.merge(candidateId, 1, Integer::sum);
                totalVotes++;
            }
        }

        // Sort candidates by votes desc, then displayOrder asc.
        candidates.sort((a, b) -> {
            int va = tallies.getOrDefault(a.getId(), 0);
            int vb = tallies.getOrDefault(b.getId(), 0);
            if (va != vb) return Integer.compare(vb, va);
            return Integer.compare(a.getDisplayOrder(), b.getDisplayOrder());
        });

        // Dense competition ranking (1, 2, 2, 4, …).
        List<CandidateResult> sorted = new ArrayList<>(candidates.size());
        int rank = 0;
        int prevVotes = Integer.MIN_VALUE;
        int positionsSeen = 0;
        for (Candidate c : candidates) {
            int votes = tallies.getOrDefault(c.getId(), 0);
            positionsSeen++;
            if (votes != prevVotes) {
                rank = positionsSeen;
                prevVotes = votes;
            }
            double percent = totalVotes > 0 ? (votes * 100.0 / totalVotes) : 0.0;
            sorted.add(new CandidateResult(
                    c.getId(), c.getName(), c.getParty(), votes, percent, rank
            ));
        }

        // Winners = all rank-1 candidates with at least one vote.
        List<CandidateResult> winners = new ArrayList<>();
        for (CandidateResult r : sorted) {
            if (r.rank() == 1 && r.votes() > 0) winners.add(r);
        }
        boolean tie = winners.size() > 1;

        int eligible = (int) voterRepo.countByElection(election);
        double turnout = eligible > 0 ? (totalVotes * 100.0 / eligible) : 0.0;

        BlockchainService.ValidationReport report = blockchainService.validateChain();
        String chainMessage = report.isValid()
                ? "Chain verified · " + report.getTotalBlocks() + " block(s)"
                : "Chain integrity error: " + String.join("; ", report.getErrors());

        if (decryptionFailures > 0) {
            log.warn("Election {}: {} block(s) failed to decrypt",
                    election.getElectionCode(), decryptionFailures);
        }

        return new ElectionResults(
                election, totalVotes, eligible, turnout,
                sorted, winners, tie,
                decryptionFailures, report.isValid(), chainMessage, Instant.now()
        );
    }
}