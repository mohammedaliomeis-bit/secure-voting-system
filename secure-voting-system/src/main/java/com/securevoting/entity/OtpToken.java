package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "otp_tokens",
        indexes = {
                @Index(name = "ix_otp_user_purpose", columnList = "user_id, purpose"),
                @Index(name = "ix_otp_voter_record", columnList = "voter_record_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user; // nullable for VOTING OTPs sent to non-account voters

    @Column(name = "purpose", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private OtpPurpose purpose;

    /** SHA-256 hash of the 6-digit code. */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "used", nullable = false)
    private boolean used = false;

    /** For VOTING OTPs: the election the voter is voting in. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "election_id")
    private Election election;

    /** For VOTING OTPs: the matched voter row, to prevent double-voting after OTP confirms. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_record_id")
    private VoterRecord voterRecord;

    /** AES-encrypted destination email (so we can resend on demand). */
    @Column(name = "destination_email_encrypted", columnDefinition = "TEXT")
    private String destinationEmailEncrypted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }
}