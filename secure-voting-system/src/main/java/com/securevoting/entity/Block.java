package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "blocks",
        indexes = {
                @Index(name = "ix_blocks_block_index", columnList = "block_index", unique = true),
                @Index(name = "ix_blocks_hash", columnList = "hash", unique = true)
        }
)
@Getter @Setter @NoArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 0-based sequential index within the chain. 0 = genesis. */
    @Column(name = "block_index", nullable = false, unique = true)
    private long blockIndex;

    /** Hex SHA-256 hash of the previous block. 64 chars. "0"×64 for genesis. */
    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    /** Hex SHA-256 hash of THIS block (over index|prevHash|timestamp|encryptedData|nonce). */
    @Column(name = "hash", nullable = false, unique = true, length = 64)
    private String hash;

    /** Proof-of-work nonce. */
    @Column(name = "nonce", nullable = false)
    private long nonce;

    /** Unix epoch millis the block was mined. */
    @Column(name = "timestamp", nullable = false)
    private long timestamp;

    /** Opaque encrypted payload. Phase 9 stores Base64(RSA(voteJson)). Genesis stores "GENESIS". */
    @Column(name = "encrypted_data", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String encryptedData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}