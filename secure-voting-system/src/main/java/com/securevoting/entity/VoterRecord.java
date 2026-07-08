package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "voter_records", indexes = {
        @Index(name = "idx_vr_election", columnList = "election_id"),
        @Index(name = "idx_vr_identity", columnList = "election_id,identity_hash", unique = true),
        @Index(name = "idx_vr_contact", columnList = "election_id,contact_hash")
})
@Getter
@Setter
public class VoterRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** SHA-256 composite hash of all identity-field values (with election salt). */
    @Column(name = "identity_hash", nullable = false, length = 64)
    private String identityHash;

    /** SHA-256 hash of the contact email (with election salt). Used for OTP lookup. */
    @Column(name = "contact_hash", nullable = false, length = 64)
    private String contactHash;

    /** AES-encrypted contact email. */
    @Column(name = "contact_encrypted", nullable = false, length = 512)
    private String contactEncrypted;

    /** Set to true once this voter has cast their ballot. */
    @Column(nullable = false)
    private boolean voted = false;

    /** Timestamp when the vote was cast (null until voted). */
    @Column(name = "voted_at")
    private Instant votedAt;

    /** Row number in the original CSV (for audit / debugging). */
    @Column(name = "csv_row_number")
    private Integer csvRowNumber;

    @OneToMany(mappedBy = "voterRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VoterRecordValue> values = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}