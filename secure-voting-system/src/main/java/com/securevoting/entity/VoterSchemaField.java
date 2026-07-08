package com.securevoting.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "voter_schema_fields",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_schema_election_key", columnNames = {"election_id", "field_key"})
        },
        indexes = {
                @Index(name = "ix_schema_election", columnList = "election_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
public class VoterSchemaField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    /** Raw column key from the CSV header (slug-ified, e.g. "student_id"). */
    @Column(name = "field_key", nullable = false, length = 100)
    private String fieldKey;

    /** Human-readable label shown in the dynamic form (e.g. "Student ID"). */
    @Column(name = "field_label", nullable = false, length = 150)
    private String fieldLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 20)
    private VoterFieldType fieldType = VoterFieldType.TEXT;

    /** If true, the voter must enter this field for identity matching. */
    @Column(name = "is_identity_field", nullable = false)
    private boolean identityField = true;

    /** Exactly one schema field per election should be the contact (email) field. */
    @Column(name = "is_contact_field", nullable = false)
    private boolean contactField = false;

    @Column(name = "required", nullable = false)
    private boolean required = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}