package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "voter_record_values", indexes = {
        @Index(name = "idx_vrv_record",   columnList = "voter_record_id"),
        @Index(name = "idx_vrv_hash",     columnList = "value_hash")
})
@Getter
@Setter
public class VoterRecordValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voter_record_id", nullable = false)
    private VoterRecord voterRecord;

    /** Schema field key (matches VoterSchemaField.fieldKey). */
    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    /** SHA-256 hash of the value (salted with election.validationSalt). */
    @Column(name = "value_hash", nullable = false, length = 64)
    private String valueHash;

    /** AES-encrypted original value. */
    @Column(name = "value_encrypted", nullable = false, length = 1024)
    private String valueEncrypted;
}