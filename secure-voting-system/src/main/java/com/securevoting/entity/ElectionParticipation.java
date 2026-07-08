package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Links a signed-in user account to an election they have voted in.
 *
 * Used to enforce one-vote-per-ACCOUNT across all CSV identities. This table
 * deliberately does NOT reference the candidate or the encrypted vote payload
 * — vote secrecy is preserved by the RSA-encrypted blockchain block. Only
 * "this account participated in this election" is recorded here.
 */
@Entity
@Table(
        name = "election_participation",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_part_user_election",
                        columnNames = {"user_id", "election_id"}
                )
        },
        indexes = {
                @Index(name = "ix_part_user", columnList = "user_id"),
                @Index(name = "ix_part_election", columnList = "election_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ElectionParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Pointer to the encrypted block on the ledger that holds this vote. */
    @Column(name = "block_index", nullable = false)
    private long blockIndex;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    @PrePersist
    void onCreate() {
        if (votedAt == null) votedAt = Instant.now();
    }
}