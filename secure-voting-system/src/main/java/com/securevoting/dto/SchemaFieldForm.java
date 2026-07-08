package com.securevoting.dto;

import com.securevoting.entity.VoterFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SchemaFieldForm {

    /** Internal column key — used in CSV headers. lower_snake_case. */
    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[a-z][a-z0-9_]*", message = "Use lowercase letters, digits, and underscores only.")
    private String fieldKey;

    /** Human-readable label shown in the UI. */
    @NotBlank @Size(max = 100)
    private String fieldLabel;

    @NotNull
    private VoterFieldType fieldType = VoterFieldType.TEXT;

    /** Used to match the voter (e.g. national ID). At least one field must be marked identity. */
    private boolean identityField = false;

    /** Used to email the voter their OTP (typically one EMAIL-typed field). Exactly one required. */
    private boolean contactField = false;

    /** If true, CSV rows missing this column are rejected. */
    private boolean required = true;
}