package com.securevoting.service;

import com.securevoting.config.AppProperties;
import com.securevoting.crypto.AesEncryptionService;
import com.securevoting.crypto.HashUtil;
import com.securevoting.entity.*;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.*;
import com.securevoting.util.CodeGenerator;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class VoteService {

    private static final Logger log = LoggerFactory.getLogger(VoteService.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");

    private final ElectionRepository electionRepo;
    private final VoterRecordRepository voterRepo;
    private final VoterSchemaFieldRepository schemaRepo;
    private final CandidateRepository candidateRepo;
    private final OtpTokenRepository otpRepo;
    private final ReceiptIndexRepository receiptRepo;
    private final VoteAuditRepository auditRepo;
    private final ElectionParticipationRepository partRepo;   // NEW (one-vote-per-account)
    private final BlockchainService blockchainService;
    private final RsaKeyService rsaKeyService;
    private final AesEncryptionService aes;
    private final JavaMailSender mailSender;
    private final AppProperties props;

    public VoteService(ElectionRepository electionRepo,
                       VoterRecordRepository voterRepo,
                       VoterSchemaFieldRepository schemaRepo,
                       CandidateRepository candidateRepo,
                       OtpTokenRepository otpRepo,
                       ReceiptIndexRepository receiptRepo,
                       VoteAuditRepository auditRepo,
                       ElectionParticipationRepository partRepo,   // NEW
                       BlockchainService blockchainService,
                       RsaKeyService rsaKeyService,
                       AesEncryptionService aes,
                       JavaMailSender mailSender,
                       AppProperties props) {
        this.electionRepo = electionRepo;
        this.voterRepo = voterRepo;
        this.schemaRepo = schemaRepo;
        this.candidateRepo = candidateRepo;
        this.otpRepo = otpRepo;
        this.receiptRepo = receiptRepo;
        this.auditRepo = auditRepo;
        this.partRepo = partRepo;
        this.blockchainService = blockchainService;
        this.rsaKeyService = rsaKeyService;
        this.aes = aes;
        this.mailSender = mailSender;
        this.props = props;
    }

    /* ===================== ONE-VOTE-PER-ACCOUNT CHECK (NEW) ===================== */

    /**
     * True if this user account has already cast a vote in this election.
     * Used by the dashboard and the /identify page to short-circuit before
     * any OTP is sent. Independent of which CSV identity they previously used.
     */
    @Transactional(readOnly = true)
    public boolean hasUserVoted(User user, Election election) {
        if (user == null || election == null) return false;
        return partRepo.existsByUserAndElection(user, election);
    }

    /* ===================== STEP A — IDENTIFY ===================== */

    @Transactional(readOnly = true)
    public VoterRecord identifyVoter(String electionCode, Map<String, String> submitted, User user) {
        Election election = loadActiveElection(electionCode);

        // (NEW) Account-level gate: same logged-in user cannot vote twice in the
        // same election by claiming a second CSV identity.
        if (user == null) {
            throw new ValidationException("You must be signed in to vote.");
        }
        if (partRepo.existsByUserAndElection(user, election)) {
            AUDIT.info("VOTE_BLOCKED_ACCOUNT_DOUBLE election={} user={}",
                    election.getElectionCode(), user.getId());
            throw new ValidationException(
                    "You have already voted in this election. Each account may only vote once per election.");
        }

        List<VoterSchemaField> idFields =
                schemaRepo.findByElectionAndIdentityFieldTrueOrderByDisplayOrderAsc(election);
        if (idFields.isEmpty()) {
            throw new ValidationException("This election has no identification fields configured.");
        }

        StringJoiner sj = new StringJoiner("|");
        for (VoterSchemaField f : idFields) {
            String raw = submitted.get(f.getFieldKey());
            if (raw == null || raw.isBlank()) {
                throw new ValidationException("Please fill in: " + f.getFieldLabel());
            }
            sj.add(raw.trim().toLowerCase(Locale.ROOT));
        }

        String composite = election.getValidationSalt() + "|" + sj;
        String identityHash = HashUtil.sha256Hex(composite);

        VoterRecord vr = voterRepo.findByElectionAndIdentityHash(election, identityHash)
                .orElseThrow(() -> new ValidationException(
                        "We couldn't match your details against the voter roll for this election."));

        // Per-CSV-row gate stays as a backstop (e.g. another account already
        // burned this identity).
        if (vr.isVoted()) {
            AUDIT.info("VOTE_BLOCKED_IDENTITY_REUSED election={} vr={}",
                    election.getElectionCode(), vr.getId());
            throw new ValidationException("Our records show this identity has already voted in this election.");
        }

        return vr;
    }

    /* ===================== STEP B — ISSUE OTP ===================== */

    @Transactional
    public void issueVotingOtp(VoterRecord voter) {
        Election election = voter.getElection();

        String code = CodeGenerator.numericOtp(props.getOtp().getLength());
        String codeHash = HashUtil.sha256Hex(code);

        OtpToken token = new OtpToken();
        token.setPurpose(OtpPurpose.VOTING);
        token.setCodeHash(codeHash);
        token.setExpiresAt(Instant.now().plus(props.getOtp().getTtlMinutes(), ChronoUnit.MINUTES));
        token.setAttempts(0);
        token.setUsed(false);
        token.setElection(election);
        token.setVoterRecord(voter);
        token.setDestinationEmailEncrypted(voter.getContactEncrypted());
        otpRepo.save(token);

        String email = aes.decrypt(voter.getContactEncrypted());
        sendVotingOtpEmail(email, code, election);

        AUDIT.info("VOTE_OTP_ISSUED election={} vr={}", election.getElectionCode(), voter.getId());
    }

    /* ===================== STEP C — VERIFY OTP ===================== */

    @Transactional
    public void verifyVotingOtp(VoterRecord voter, String submittedCode) {
        Election election = voter.getElection();

        OtpToken token = otpRepo
                .findTopByElectionAndVoterRecordAndPurposeAndUsedFalseOrderByCreatedAtDesc(
                        election, voter, OtpPurpose.VOTING)
                .orElseThrow(() -> new ValidationException("No active OTP — please request a new one."));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException("This OTP has expired. Please request a new one.");
        }
        if (token.getAttempts() >= props.getOtp().getMaxAttempts()) {
            throw new ValidationException("Too many failed attempts on this OTP. Request a new one.");
        }

        String submittedHash = HashUtil.sha256Hex(submittedCode == null ? "" : submittedCode.trim());
        if (!constantTimeEquals(submittedHash, token.getCodeHash())) {
            token.setAttempts(token.getAttempts() + 1);
            otpRepo.save(token);
            AUDIT.info("VOTE_OTP_BAD election={} vr={} attempts={}",
                    election.getElectionCode(), voter.getId(), token.getAttempts());
            throw new ValidationException("Incorrect code. Please try again.");
        }

        token.setUsed(true);
        otpRepo.save(token);
        AUDIT.info("VOTE_OTP_OK election={} vr={}", election.getElectionCode(), voter.getId());
    }

    /* ===================== STEP D — CAST VOTE ===================== */

    public record VoteReceipt(String receiptCode, long blockIndex, String electionTitle) {}

    @Transactional
    public synchronized VoteReceipt castVote(VoterRecord voter, Long candidateId, User user) {
        if (user == null) {
            throw new ValidationException("You must be signed in to vote.");
        }

        VoterRecord fresh = voterRepo.findById(voter.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Voter record gone"));
        if (fresh.isVoted()) {
            throw new ValidationException("This voter record has already been used.");
        }

        Election election = fresh.getElection();

        // (NEW) Defensive re-check under the same transaction; the unique
        // constraint on (user, election) is the ultimate guarantee.
        if (partRepo.existsByUserAndElection(user, election)) {
            AUDIT.info("VOTE_BLOCKED_ACCOUNT_DOUBLE_CAST election={} user={}",
                    election.getElectionCode(), user.getId());
            throw new ValidationException("You have already voted in this election.");
        }

        if (election.getStatus() != ElectionStatus.ACTIVE) {
            throw new ValidationException("This election is no longer accepting votes.");
        }
        Instant now = Instant.now();
        if (now.isBefore(election.getStartTime()) || now.isAfter(election.getEndTime())) {
            throw new ValidationException("Voting window is closed.");
        }

        Candidate candidate = candidateRepo.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found"));
        if (!candidate.getElection().getId().equals(election.getId())) {
            throw new ValidationException("That candidate is not on this election's ballot.");
        }

        String payload = """
                {"v":1,"electionCode":"%s","candidateId":%d,"ts":%d}"""
                .formatted(election.getElectionCode(), candidate.getId(), now.toEpochMilli());
        String encrypted = rsaKeyService.encryptFromString(payload);

        Block block = blockchainService.addBlock(encrypted);

        String receipt = CodeGenerator.receipt16();
        String receiptHash = HashUtil.sha256Hex(receipt);

        ReceiptIndex idx = new ReceiptIndex();
        idx.setReceiptHash(receiptHash);
        idx.setElection(election);
        idx.setBlockIndex(block.getBlockIndex());
        receiptRepo.save(idx);

        fresh.setVoted(true);
        fresh.setVotedAt(now);
        voterRepo.save(fresh);

        // (NEW) Lock this account out of further votes in this election.
        ElectionParticipation part = new ElectionParticipation();
        part.setUser(user);
        part.setElection(election);
        part.setBlockIndex(block.getBlockIndex());
        part.setVotedAt(now);
        partRepo.save(part);

        VoteAudit audit = new VoteAudit();
        audit.setElection(election);
        audit.setBlockIndex(block.getBlockIndex());
        audit.setVotedAt(now);
        auditRepo.save(audit);

        AUDIT.info("VOTE_CAST election={} vr={} block={}",
                election.getElectionCode(), fresh.getId(), block.getBlockIndex());
        log.info("Vote recorded in election {} at block #{}",
                election.getElectionCode(), block.getBlockIndex());

        return new VoteReceipt(formatReceipt(receipt), block.getBlockIndex(), election.getTitle());
    }

    /* ===================== Helpers ===================== */

    public List<Candidate> getCandidates(Election election) {
        return candidateRepo.findByElectionOrderByDisplayOrderAsc(election);
    }

    public List<VoterSchemaField> getIdentityFields(Election election) {
        return schemaRepo.findByElectionAndIdentityFieldTrueOrderByDisplayOrderAsc(election);
    }

    public Election loadActiveElection(String code) {
        Election e = electionRepo.findByElectionCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found: " + code));
        if (e.getStatus() != ElectionStatus.ACTIVE) {
            throw new ValidationException("This election is not currently open for voting.");
        }
        Instant now = Instant.now();
        if (now.isBefore(e.getStartTime()) || now.isAfter(e.getEndTime())) {
            throw new ValidationException("This election is outside its voting window.");
        }
        return e;
    }

    /**
     * Legacy helper retained for backward compatibility. The dashboard no
     * longer filters live elections by CSV-contact-hash match; this method
     * is unused by the dashboard but kept in case any other caller relies
     * on it. Safe to delete later.
     */
    @Transactional(readOnly = true)
    public List<VoterRecord> availableForUser(User user) {
        String email;
        try {
            email = aes.decrypt(user.getEmailEncrypted());
        } catch (Exception ex) {
            log.warn("Could not decrypt user {} email for availability check", user.getId());
            return List.of();
        }
        if (email == null || email.isBlank()) return List.of();

        String lower = email.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        List<Election> active = electionRepo.findByStatusAndStartTimeBeforeAndEndTimeAfter(
                ElectionStatus.ACTIVE, now, now);

        List<VoterRecord> out = new ArrayList<>(active.size());
        for (Election e : active) {
            String contactHash = HashUtil.sha256Hex(lower + e.getValidationSalt());
            voterRepo.findByElectionAndContactHash(e, contactHash)
                    .filter(vr -> !vr.isVoted())
                    .ifPresent(out::add);
        }
        return out;
    }

    private String formatReceipt(String raw16) {
        return raw16.substring(0, 4) + "-" + raw16.substring(4, 8) + "-"
                + raw16.substring(8, 12) + "-" + raw16.substring(12, 16);
    }

    private void sendVotingOtpEmail(String to, String code, Election election) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, false, StandardCharsets.UTF_8.name());
            h.setFrom(props.getMail().getFrom());
            h.setTo(to);
            h.setSubject("Your voting code for " + election.getTitle());
            h.setText("""
                    Hello,

                    Use this 6-digit code to cast your vote in "%s":

                          %s

                    This code expires in %d minutes. If you didn't request it, ignore this email.

                    — Secure Voting System
                    """.formatted(election.getTitle(), code, props.getOtp().getTtlMinutes()),
                    false);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Failed to send voting OTP email", e);
            throw new ValidationException("We couldn't send the verification email. Please try again.");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}