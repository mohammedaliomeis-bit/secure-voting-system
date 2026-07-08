package com.securevoting.service;

import com.securevoting.dto.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class ChatbotService {

    /** A single FAQ rule. */
    private record FaqIntent(
            String id,
            List<String> keywords,   // any-of, case-insensitive substring match
            int weight,              // tiebreaker; higher = preferred
            String answer,
            List<String> suggestions
    ) {}

    private static final List<FaqIntent> INTENTS = List.of(
            new FaqIntent(
                    "identify_help",
                    List.of("identify", "identity", "national id", "id number", "match",
                            "can't find", "cant find", "not found", "no match", "doesn't match", "doesnt match"),
                    10,
                    "Enter the identity values exactly as they appear on the voter roll for this " +
                            "election. Spacing is ignored and matching is case-insensitive, but every " +
                            "required identity field must be filled. If the organizer set up multiple " +
                            "identity fields (e.g. national ID + date of birth), fill in all of them. " +
                            "If you're sure your values are correct and still get a mismatch, contact the organizer.",
                    List.of("What's an OTP?", "Why am I not on the roll?", "How do I contact the organizer?")
            ),
            new FaqIntent(
                    "otp_not_arrived",
                    List.of("otp", "code", "didn't receive", "didnt receive", "didn't arrive", "didnt arrive",
                            "not received", "no email", "where is", "spam", "junk", "email not"),
                    8,
                    "Voting codes are emailed within ~30 seconds. If you don't see one:\n" +
                            "• Check your spam / junk folder.\n" +
                            "• Confirm the email shown on the OTP page matches yours.\n" +
                            "• Click \"Resend code\" — codes are valid for 10 minutes.\n" +
                            "• Some corporate inboxes delay external mail by a few minutes.",
                    List.of("OTP expired — what now?", "OTP says invalid", "Contact the organizer")
            ),
            new FaqIntent(
                    "otp_expired",
                    List.of("expired", "expire", "too late", "ran out", "timeout"),
                    9,
                    "OTP codes expire 10 minutes after they are issued, for security. " +
                            "Just click \"Resend code\" on the OTP page — a fresh code will be emailed " +
                            "to you and the old one is invalidated immediately.",
                    List.of("OTP didn't arrive", "OTP says invalid", "How many tries do I get?")
            ),
            new FaqIntent(
                    "otp_wrong",
                    List.of("wrong code", "invalid code", "incorrect code", "wrong otp", "invalid otp",
                            "code not working", "doesnt work", "doesn't work"),
                    9,
                    "Double-check you typed all 6 digits with no spaces. You have up to 5 attempts " +
                            "per code; after that the code locks and you must request a new one. " +
                            "Make sure you're using the most recent code from your inbox if you requested " +
                            "more than one.",
                    List.of("Resend OTP", "OTP expired", "Contact the organizer")
            ),
            new FaqIntent(
                    "already_voted",
                    List.of("already voted", "voted twice", "vote again", "second vote", "duplicate"),
                    10,
                    "Each voter on the roll can cast exactly one ballot per election — this is " +
                            "enforced both in the database and on the blockchain. If the system says you've " +
                            "already voted but you believe you haven't, contact the organizer with your " +
                            "identity details so they can audit the receipt index.",
                    List.of("How do I verify my receipt?", "Contact the organizer")
            ),
            new FaqIntent(
                    "election_not_active",
                    List.of("not active", "not open", "closed", "not started", "ended", "over",
                            "outside window", "voting closed"),
                    8,
                    "Elections only accept votes between their start and end times. Check the " +
                            "election details page for the schedule. If you arrived early, refresh after " +
                            "the start time. If it's ended, voting is permanently closed — but the " +
                            "results page will be available.",
                    List.of("Where do I see results?", "How does the blockchain work?")
            ),
            new FaqIntent(
                    "receipt_help",
                    List.of("receipt", "verify vote", "verify my", "confirmation", "proof", "track"),
                    9,
                    "After voting you receive a 16-character receipt code in the format " +
                            "AAAA-BBBB-CCCC-DDDD. Save it. Visit the /verify page and paste the receipt " +
                            "to confirm your vote was recorded in the blockchain. The verification will " +
                            "NOT reveal who you voted for — it only proves your ballot is in the chain.",
                    List.of("How does the blockchain work?", "I lost my receipt", "Are votes anonymous?")
            ),
            new FaqIntent(
                    "lost_receipt",
                    List.of("lost receipt", "lost code", "forgot receipt", "lost my receipt", "no receipt"),
                    10,
                    "Receipts are intentionally not stored in a way that lets us re-issue them — " +
                            "this protects your anonymity. If you've lost yours, you can still confirm " +
                            "your vote was counted by checking the election's total vote count on the " +
                            "results page, but you cannot re-verify a specific receipt.",
                    List.of("Are votes anonymous?", "How does the blockchain work?")
            ),
            new FaqIntent(
                    "blockchain_what",
                    List.of("blockchain", "block chain", "chain", "block", "hash", "tamper", "proof of work",
                            "how does it work", "secure"),
                    7,
                    "Every vote is encrypted with RSA-2048 and appended to a tamper-evident " +
                            "blockchain stored on the server. Each block is linked to the previous one " +
                            "with a SHA-256 hash and sealed with proof-of-work (4 leading zeros). Any " +
                            "modification breaks the chain and is detected by the verification page.",
                    List.of("Are votes anonymous?", "How do I verify my receipt?", "What encryption is used?")
            ),
            new FaqIntent(
                    "anonymity",
                    List.of("anonymous", "anonymity", "private", "who voted", "see my vote", "secret ballot"),
                    10,
                    "Votes are anonymous. The blockchain stores only the encrypted ballot " +
                            "(electionCode + candidateId + timestamp), with no link to your identity. " +
                            "Your voter record is separately marked as \"voted\" but is not connected to " +
                            "any specific block. Only the election organizer's RSA private key can " +
                            "decrypt ballots — and only the aggregate tallies are exposed.",
                    List.of("How does the blockchain work?", "What encryption is used?")
            ),
            new FaqIntent(
                    "encryption",
                    List.of("encryption", "encrypted", "rsa", "aes", "crypto", "key"),
                    7,
                    "We use AES-256-CBC to encrypt voter PII at rest (emails, names), RSA-2048 " +
                            "with OAEP padding to encrypt each ballot, BCrypt (cost 12) for password " +
                            "hashing, and SHA-256 for all blockchain hashes and identity lookups.",
                    List.of("How does the blockchain work?", "Are votes anonymous?")
            ),
            new FaqIntent(
                    "register_help",
                    List.of("register", "sign up", "signup", "create account", "new account"),
                    8,
                    "Click \"Create one\" on the login page. You'll provide your name, email, " +
                            "date of birth, and a password. A 6-digit OTP will be emailed to verify your " +
                            "address — enter it to activate your account, then sign in.",
                    List.of("OTP didn't arrive", "Forgot my password", "How do I vote?")
            ),
            new FaqIntent(
                    "forgot_password",
                    List.of("forgot password", "reset password", "lost password", "change password"),
                    10,
                    "Password reset isn't available in this version of the system. Contact the " +
                            "organizer directly — they can re-issue your access if needed.",
                    List.of("Contact the organizer", "How do I register?")
            ),
            new FaqIntent(
                    "results_help",
                    List.of("result", "results", "winner", "tally", "count", "outcome"),
                    8,
                    "Once an election is active, the organizer can open the Results page from " +
                            "the election editor. It shows total votes, turnout, per-candidate counts " +
                            "and percentages, a winner badge (or tie notice), and a live blockchain " +
                            "validity check.",
                    List.of("How does the blockchain work?", "Are votes anonymous?")
            ),
            new FaqIntent(
                    "contact",
                    List.of("contact", "support", "help me", "human", "organizer", "admin"),
                    6,
                    "Reach out to the person who created your election — their email is on the " +
                            "election details page. For account or login issues, contact your system " +
                            "administrator.",
                    List.of("Forgot my password", "How do I register?")
            ),
            new FaqIntent(
                    "greeting",
                    List.of("hi", "hello", "hey", "salam", "marhaba", "good morning", "good evening"),
                    3,
                    "Hi! I can help with identity verification, OTP codes, voting, receipts, " +
                            "and blockchain verification. What would you like to know?",
                    List.of("How do I vote?", "OTP didn't arrive", "How does the blockchain work?")
            ),
            new FaqIntent(
                    "how_to_vote",
                    List.of("how to vote", "how do i vote", "cast vote", "voting process", "steps"),
                    8,
                    "1. Open the election link or click \"Vote now\" on your dashboard.\n" +
                            "2. Enter your identity details from the voter roll.\n" +
                            "3. Receive a 6-digit code by email.\n" +
                            "4. Enter the code on the OTP page.\n" +
                            "5. Select your candidate and confirm.\n" +
                            "6. Save the receipt you receive.",
                    List.of("OTP didn't arrive", "How do I verify my receipt?", "Are votes anonymous?")
            )
    );

    private static final ChatResponse FALLBACK = new ChatResponse(
            "fallback",
            "I'm not sure I understood that. I can help with: identity verification, " +
                    "OTP codes, the voting process, receipts, blockchain verification, " +
                    "anonymity, registration, and contacting your organizer.",
            List.of("How do I vote?", "OTP didn't arrive", "How does the blockchain work?")
    );

    public ChatResponse ask(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return FALLBACK;
        }
        String msg = userMessage.toLowerCase(Locale.ROOT).trim();

        FaqIntent best = null;
        int bestScore = 0;

        for (FaqIntent intent : INTENTS) {
            int score = 0;
            for (String kw : intent.keywords()) {
                if (msg.contains(kw)) {
                    // Longer keywords score higher (more specific matches win).
                    score += kw.length();
                }
            }
            if (score > 0) {
                score += intent.weight();
                if (score > bestScore) {
                    bestScore = score;
                    best = intent;
                }
            }
        }

        if (best == null) {
            return FALLBACK;
        }
        return new ChatResponse(best.id(), best.answer(), best.suggestions());
    }
}