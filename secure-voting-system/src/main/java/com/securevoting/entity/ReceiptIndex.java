package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "receipt_index",
        indexes = {
                @Index(name = "ix_receipt_hash", columnList = "receipt_hash", unique = true),
                @Index(name = "ix_receipt_election", columnList = "election_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class ReceiptIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hex of the 16-digit receipt code shown to the voter. */
    @Column(name = "receipt_hash", nullable = false, length = 64, unique = true)
    private String receiptHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Block index that holds the corresponding encrypted vote. */
    @Column(name = "block_index", nullable = false)
    private long blockIndex;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }
}