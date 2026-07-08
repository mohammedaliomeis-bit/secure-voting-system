package com.securevoting.service;

import com.securevoting.crypto.AesEncryptionService;
import com.securevoting.entity.*;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.InvitationRepository;
import com.securevoting.repository.VoterRecordRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Service
@Transactional
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final Logger AUDIT = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final SecureRandom RNG = new SecureRandom();
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy 'at' HH:mm z")
                    .withZone(ZoneId.systemDefault());

    private final InvitationRepository invitationRepository;
    private final VoterRecordRepository voterRecordRepository;
    private final AesEncryptionService aes;
    private final JavaMailSender mailSender;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${app.mail.from}")
    private String mailFrom;

    public InvitationService(InvitationRepository invitationRepository,
                             VoterRecordRepository voterRecordRepository,
                             AesEncryptionService aes,
                             JavaMailSender mailSender) {
        this.invitationRepository = invitationRepository;
        this.voterRecordRepository = voterRecordRepository;
        this.aes = aes;
        this.mailSender = mailSender;
    }

    // ============ Generation ============

    /**
     * Idempotently create PENDING invitations for every voter on the roll.
     * Safe to call multiple times — only creates rows for voters who don't
     * already have an invitation.
     */
    public int generateForElection(Election election) {
        List<VoterRecord> voters = voterRecordRepository.findByElection(election);
        if (voters.isEmpty()) {
            throw new ValidationException("Upload the voter roll before generating invitations.");
        }
        int created = 0;
        for (VoterRecord vr : voters) {
            if (invitationRepository.findByElectionAndVoterRecord(election, vr).isPresent()) continue;
            Invitation inv = new Invitation();
            inv.setElection(election);
            inv.setVoterRecord(vr);
            inv.setToken(generateToken());
            inv.setStatus(InvitationStatus.PENDING);
            inv.setExpiresAt(election.getEndTime());
            invitationRepository.save(inv);
            created++;
        }
        AUDIT.info("Generated {} invitations for election {}", created, election.getElectionCode());
        return created;
    }

    // ============ Sending ============

    /**
     * Generate any missing invitations, then email every PENDING/SENT one
     * that hasn't been accepted or revoked.
     */
    public int generateAndSendAll(Election election) {
        generateForElection(election);
        List<Invitation> all = invitationRepository.findByElectionOrderByCreatedAtDesc(election);
        int sent = 0;
        for (Invitation inv : all) {
            if (inv.getStatus() == InvitationStatus.ACCEPTED
                    || inv.getStatus() == InvitationStatus.REVOKED
                    || inv.getStatus() == InvitationStatus.EXPIRED) continue;
            if (sendOne(inv, election)) sent++;
        }
        AUDIT.info("Sent {} invitations for election {}", sent, election.getElectionCode());
        return sent;
    }

    public boolean resend(Invitation invitation) {
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new ValidationException("Cannot resend a revoked invitation. Reissue it instead.");
        }
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ValidationException("This invitation has already been accepted.");
        }
        return sendOne(invitation, invitation.getElection());
    }

    private boolean sendOne(Invitation inv, Election election) {
        try {
            String recipient = decryptVoterEmail(inv.getVoterRecord());
            String link = baseUrl + "/vote/invite/" + inv.getToken();

            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(mime, false, StandardCharsets.UTF_8.name());
            h.setFrom(mailFrom);
            h.setTo(recipient);
            h.setSubject("You're invited to vote: " + election.getTitle());
            h.setText(buildEmailBody(election, link), true);
            mailSender.send(mime);

            inv.setStatus(InvitationStatus.SENT);
            inv.setSentAt(Instant.now());
            invitationRepository.save(inv);
            return true;
        } catch (MessagingException ex) {
            log.warn("Failed to send invitation {} ({}): {}",
                    inv.getId(), election.getElectionCode(), ex.getMessage());
            return false;
        }
    }

    private String buildEmailBody(Election election, String link) {
        String start = FMT.format(election.getStartTime());
        String end = FMT.format(election.getEndTime());
        return """
               <!DOCTYPE html>
               <html><body style="font-family: -apple-system, Segoe UI, Arial, sans-serif; color: #212529; line-height: 1.6; max-width: 560px; margin: 0 auto; padding: 24px;">
                 <h2 style="color: #4263eb; margin-bottom: 8px;">You're invited to vote</h2>
                 <p style="color: #495057;">You've been invited to participate in the following secure election:</p>
                 <div style="background: #f8f9fa; border-left: 4px solid #4263eb; padding: 16px; margin: 20px 0;">
                   <h3 style="margin: 0 0 8px 0;">%s</h3>
                   <p style="margin: 4px 0; font-size: 14px; color: #495057;">Election ID: <code>%s</code></p>
                   <p style="margin: 4px 0; font-size: 14px; color: #495057;">Voting window: %s — %s</p>
                 </div>
                 <p>Click the secure link below to add this election to your dashboard:</p>
                 <p style="text-align: center; margin: 28px 0;">
                   <a href="%s" style="background: #4263eb; color: #fff; padding: 12px 28px; border-radius: 6px; text-decoration: none; font-weight: 600; display: inline-block;">Accept Invitation</a>
                 </p>
                 <p style="font-size: 13px; color: #868e96;">
                   Or copy and paste this link into your browser:<br>
                   <span style="word-break: break-all;">%s</span>
                 </p>
                 <hr style="border: none; border-top: 1px solid #dee2e6; margin: 28px 0;">
                 <p style="font-size: 13px; color: #868e96;">
                   <strong>Important:</strong> This link is unique to you and can be used only once. Do not share it with anyone — if someone else opens it first, you'll lose access and need a new invitation from the organizer.
                 </p>
                 <p style="font-size: 13px; color: #868e96;">
                   You'll be asked to sign in (or create an account) before the invitation activates. After that, the election appears on your dashboard and you can cast your vote when voting opens.
                 </p>
               </body></html>
               """.formatted(
                escape(election.getTitle()),
                escape(election.getElectionCode()),
                start, end,
                link, link
        );
    }

    // ============ Acceptance ============

    /**
     * Called after a voter clicks /vote/invite/{token} and is signed in.
     * Binds the invitation to that user account permanently.
     */
    public Invitation acceptInvitation(String token, User user) {
        Invitation inv = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "This invitation link is not valid."));

        // Idempotent: if this same user already accepted, just return it.
        if (inv.getStatus() == InvitationStatus.ACCEPTED) {
            if (inv.getAcceptedByUser() != null
                    && inv.getAcceptedByUser().getId().equals(user.getId())) {
                return inv;
            }
            throw new ValidationException(
                    "This invitation has already been used by another account. " +
                            "Contact the election organizer to request a new one.");
        }
        if (inv.getStatus() == InvitationStatus.REVOKED) {
            throw new ValidationException("This invitation has been revoked by the organizer.");
        }
        if (inv.isExpired() || inv.getStatus() == InvitationStatus.EXPIRED) {
            inv.setStatus(InvitationStatus.EXPIRED);
            invitationRepository.save(inv);
            throw new ValidationException("This invitation has expired.");
        }

        inv.setStatus(InvitationStatus.ACCEPTED);
        inv.setAcceptedAt(Instant.now());
        inv.setAcceptedByUser(user);
        Invitation saved = invitationRepository.save(inv);
        AUDIT.info("Invitation {} accepted by user {} for election {}",
                inv.getId(), user.getId(), inv.getElection().getElectionCode());
        return saved;
    }

    // ============ Organizer actions ============

    public void revoke(Invitation invitation) {
        if (invitation.getStatus() == InvitationStatus.REVOKED) return;
        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRevokedAt(Instant.now());
        invitationRepository.save(invitation);
        AUDIT.info("Invitation {} revoked for election {}",
                invitation.getId(), invitation.getElection().getElectionCode());
    }

    /** Generates a fresh token for a revoked invitation and resets its status. */
    public void reissue(Invitation invitation) {
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new ValidationException(
                    "Cannot reissue an accepted invitation. Revoke it first if needed.");
        }
        invitation.setToken(generateToken());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setSentAt(null);
        invitation.setRevokedAt(null);
        invitationRepository.save(invitation);
    }

    @Transactional(readOnly = true)
    public Invitation requireById(Long id) {
        return invitationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found."));
    }

    @Transactional(readOnly = true)
    public List<Invitation> listForElection(Election election) {
        return invitationRepository.findByElectionOrderByCreatedAtDesc(election);
    }

    // ============ Helpers ============

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Decrypt the voter's contact email from VoterRecord.
     * Mirrors VoteService.issueVotingOtp's pattern exactly.
     */
    private String decryptVoterEmail(VoterRecord vr) {
        String cipher = vr.getContactEncrypted();
        if (cipher == null || cipher.isBlank()) {
            throw new ValidationException(
                    "Voter row has no contact email. Re-upload the voter roll.");
        }
        return aes.decrypt(cipher);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}