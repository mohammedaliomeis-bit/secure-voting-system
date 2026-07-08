package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "ix_users_email_hash", columnList = "email_hash", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name_encrypted", nullable = false, columnDefinition = "TEXT")
    private String firstNameEncrypted;

    @Column(name = "last_name_encrypted", nullable = false, columnDefinition = "TEXT")
    private String lastNameEncrypted;

    /** SHA-256(lowercase email) — used as the unique lookup key. */
    @Column(name = "email_hash", nullable = false, length = 64, unique = true)
    private String emailHash;

    /** AES-256-CBC ciphertext of the email (Base64). */
    @Column(name = "email_encrypted", nullable = false, columnDefinition = "TEXT")
    private String emailEncrypted;

    @Column(name = "dob", nullable = false)
    private LocalDate dateOfBirth;

    /** BCrypt hash — never plaintext. */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = false;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}