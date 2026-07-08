package com.securevoting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

public class CandidateForm {

    @NotBlank
    @Size(max = 150)
    private String name;

    @Size(max = 100)
    private String party;

    @Size(max = 1000)
    private String description;

    /** Optional photo. Validated by CandidatePhotoService. */
    private MultipartFile photo;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getParty() { return party; }
    public void setParty(String party) { this.party = party; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public MultipartFile getPhoto() { return photo; }
    public void setPhoto(MultipartFile photo) { this.photo = photo; }
}