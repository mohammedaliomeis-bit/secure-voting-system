package com.securevoting.service;

import com.securevoting.dto.CandidateForm;
import com.securevoting.dto.ElectionForm;
import com.securevoting.dto.SchemaFieldForm;
import com.securevoting.entity.*;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.*;
import com.securevoting.util.CodeGenerator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

@Service
@Transactional
public class ElectionService {

    private final ElectionRepository electionRepository;
    private final CandidateRepository candidateRepository;
    private final VoterSchemaFieldRepository schemaFieldRepository;
    private final VoterRecordRepository voterRecordRepository;

    public ElectionService(ElectionRepository electionRepository,
                           CandidateRepository candidateRepository,
                           VoterSchemaFieldRepository schemaFieldRepository,
                           VoterRecordRepository voterRecordRepository) {
        this.electionRepository = electionRepository;
        this.candidateRepository = candidateRepository;
        this.schemaFieldRepository = schemaFieldRepository;
        this.voterRecordRepository = voterRecordRepository;
    }

    // ---------- lookup ----------
    @Transactional(readOnly = true)
    public Election requireByCode(String code) {
        return electionRepository.findByElectionCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found."));
    }

    public boolean isOrganizer(Election election, User user) {
        return user != null && election.getCreator() != null
                && election.getCreator().getId().equals(user.getId());
    }

    public void requireOwner(Election election, User user) {
        if (!isOrganizer(election, user)) {
            throw new AccessDeniedException("Only the organizer can manage this election.");
        }
    }

    public void requireDraft(Election election) {
        if (election.getStatus() != ElectionStatus.DRAFT) {
            throw new ValidationException("Only DRAFT elections can be modified.");
        }
    }

    // ---------- create / update ----------
    public Election createElection(ElectionForm form, User creator) {
        validateWindow(form);
        Election e = new Election();
        e.setElectionCode(CodeGenerator.electionCode());
        e.setTitle(form.getTitle().trim());
        e.setDescription(trimToNull(form.getDescription()));
        e.setStatus(ElectionStatus.DRAFT);
        e.setStartTime(toInstant(form.getStartTime()));
        e.setEndTime(toInstant(form.getEndTime()));
        e.setMaxVotesPerVoter(form.getMaxVotesPerVoter() == null ? 1 : form.getMaxVotesPerVoter());
        e.setValidationSalt(CodeGenerator.base64Salt(48));
        e.setSchemaLocked(false);
        e.setCreator(creator);
        e.setCreatedAt(Instant.now());
        return electionRepository.save(e);
    }

    public void updateMeta(Election election, ElectionForm form) {
        requireDraft(election);
        validateWindow(form);
        election.setTitle(form.getTitle().trim());
        election.setDescription(trimToNull(form.getDescription()));
        election.setStartTime(toInstant(form.getStartTime()));
        election.setEndTime(toInstant(form.getEndTime()));
        election.setMaxVotesPerVoter(form.getMaxVotesPerVoter() == null ? 1 : form.getMaxVotesPerVoter());
        electionRepository.save(election);
    }

    // ---------- candidates ----------
    public Candidate addCandidate(Election election, CandidateForm form, String storedPhotoName) {
        requireDraft(election);
        Candidate c = new Candidate();
        c.setElection(election);
        c.setName(form.getName().trim());
        c.setParty(trimToNull(form.getParty()));
        c.setDescription(trimToNull(form.getDescription()));
        c.setPhotoPath(storedPhotoName);
        long existing = candidateRepository.countByElection(election);
        c.setDisplayOrder((int) existing);
        return candidateRepository.save(c);
    }

    /** Returns the photo filename (if any) so the caller can delete it from disk. */
    public String removeCandidate(Election election, Long candidateId) {
        requireDraft(election);
        Candidate c = candidateRepository.findByIdAndElection(candidateId, election)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found."));
        String stored = c.getPhotoPath();
        candidateRepository.delete(c);
        return stored;
    }

    // ---------- schema ----------
    public VoterSchemaField addSchemaField(Election election, SchemaFieldForm form) {
        requireDraft(election);
        if (election.isSchemaLocked()) {
            throw new ValidationException("Schema is locked.");
        }
        VoterSchemaField f = new VoterSchemaField();
        f.setElection(election);
        f.setFieldKey(form.getFieldKey().trim().toLowerCase());
        f.setFieldLabel(form.getFieldLabel().trim());
        f.setFieldType(form.getFieldType());
        f.setIdentityField(form.isIdentityField());
        f.setContactField(form.isContactField());
        f.setRequired(form.isRequired());
        long existing = schemaFieldRepository.countByElection(election);
        f.setDisplayOrder((int) existing);
        return schemaFieldRepository.save(f);
    }

    public void removeSchemaField(Election election, Long fieldId) {
        requireDraft(election);
        if (election.isSchemaLocked()) {
            throw new ValidationException("Schema is locked.");
        }
        VoterSchemaField f = schemaFieldRepository.findByIdAndElection(fieldId, election)
                .orElseThrow(() -> new ResourceNotFoundException("Field not found."));
        schemaFieldRepository.delete(f);
    }

    public void lockSchema(Election election) {
        requireDraft(election);
        List<VoterSchemaField> fields = schemaFieldRepository.findByElectionOrderByDisplayOrderAsc(election);
        if (fields.isEmpty()) {
            throw new ValidationException("Add at least one schema field before locking.");
        }
        long identity = fields.stream().filter(VoterSchemaField::isIdentityField).count();
        long contact  = fields.stream().filter(VoterSchemaField::isContactField).count();
        if (identity == 0) {
            throw new ValidationException("Mark at least one field as an identity field.");
        }
        if (contact != 1) {
            throw new ValidationException("Mark exactly one field as the contact (email) field.");
        }
        boolean contactIsEmail = fields.stream()
                .filter(VoterSchemaField::isContactField)
                .findFirst()
                .map(f -> f.getFieldType() == VoterFieldType.EMAIL)
                .orElse(false);
        if (!contactIsEmail) {
            throw new ValidationException("The contact field must be of type EMAIL.");
        }
        election.setSchemaLocked(true);
        electionRepository.save(election);
    }

    // ---------- lifecycle ----------

    /**
     * Promote a DRAFT election to SCHEDULED. The scheduler will then auto-activate
     * it when startTime arrives and auto-close it when endTime arrives.
     *
     * Requires: schema locked, ≥2 candidates, ≥1 voter, startTime strictly in the future.
     */
    public void promoteToScheduled(Election election) {
        requireDraft(election);
        if (!election.isSchemaLocked()) {
            throw new ValidationException("Lock the voter schema before scheduling.");
        }
        long candidates = candidateRepository.countByElection(election);
        if (candidates < 2) {
            throw new ValidationException("Add at least 2 candidates before scheduling.");
        }
        long voters = voterRecordRepository.countByElection(election);
        if (voters < 1) {
            throw new ValidationException("Upload the voter roll before scheduling.");
        }
        Instant now = Instant.now();
        if (!election.getStartTime().isAfter(now)) {
            throw new ValidationException(
                    "Start time must be in the future to schedule. Edit the election window first.");
        }
        if (!election.getEndTime().isAfter(election.getStartTime())) {
            throw new ValidationException("End time must be after start time.");
        }
        election.setStatus(ElectionStatus.SCHEDULED);
        electionRepository.save(election);
    }

    /**
     * Revert a SCHEDULED election back to DRAFT. Only allowed while startTime is still
     * in the future — once the election goes live, the only way out is to close it.
     */
    public void revertToDraft(Election election) {
        if (election.getStatus() != ElectionStatus.SCHEDULED) {
            throw new ValidationException("Only SCHEDULED elections can be reverted to draft.");
        }
        if (!election.getStartTime().isAfter(Instant.now())) {
            throw new ValidationException(
                    "Cannot revert — the election's start time has already arrived.");
        }
        election.setStatus(ElectionStatus.DRAFT);
        electionRepository.save(election);
    }

    /**
     * Legacy manual activation (DRAFT → ACTIVE) — kept for emergency/test paths.
     * Production flow should use {@link #promoteToScheduled} + the scheduler.
     */
    public void activate(Election election) {
        requireDraft(election);
        if (!election.isSchemaLocked()) {
            throw new ValidationException("Lock the voter schema before activating.");
        }
        long candidates = candidateRepository.countByElection(election);
        if (candidates < 2) {
            throw new ValidationException("Add at least 2 candidates before activating.");
        }
        long voters = voterRecordRepository.countByElection(election);
        if (voters < 1) {
            throw new ValidationException("Upload the voter roll before activating.");
        }
        if (election.getEndTime().isBefore(Instant.now())) {
            throw new ValidationException("End time must be in the future.");
        }
        election.setStatus(ElectionStatus.ACTIVE);
        electionRepository.save(election);
    }
    

    public void close(Election election) {
        if (election.getStatus() != ElectionStatus.ACTIVE) {
            throw new ValidationException("Only ACTIVE elections can be closed.");
        }
        election.setStatus(ElectionStatus.CLOSED);
        electionRepository.save(election);
    }

    /** Deletes a DRAFT election. SCHEDULED elections must be reverted first. */
    public void delete(Election election) {
        if (election.getStatus() != ElectionStatus.DRAFT) {
            throw new ValidationException("Only DRAFT elections can be deleted.");
        }
        electionRepository.delete(election);
    }

    // ---------- helpers ----------
    private static Instant toInstant(java.time.LocalDateTime ldt) {
        if (ldt == null) throw new ValidationException("Date is required.");
        return ldt.atZone(ZoneId.systemDefault()).toInstant();
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private void validateWindow(ElectionForm form) {
        if (form.getStartTime() == null || form.getEndTime() == null) {
            throw new ValidationException("Start and end times are required.");
        }
        if (!form.getEndTime().isAfter(form.getStartTime())) {
            throw new ValidationException("End time must be after start time.");
        }
    }
}