package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "vote_audit",
        indexes = {
                @Index(name = "ix_audit_election", columnList = "election_id"),
                @Index(name = "ix_audit_block_index", columnList = "block_index", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VoteAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Index of the block in the persistent ledger that holds the encrypted vote. */
    @Column(name = "block_index", nullable = false, unique = true)
    private long blockIndex;

    @Column(name = "voted_at", nullable = false)
    private Instant votedAt;

    @PrePersist
    void onCreate() {
        if (votedAt == null) votedAt = Instant.now();
    }
}