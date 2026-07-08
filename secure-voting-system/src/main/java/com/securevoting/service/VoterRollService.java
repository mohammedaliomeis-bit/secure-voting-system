package com.securevoting.service;

import com.securevoting.config.AppProperties;
import com.securevoting.crypto.AesEncryptionService;
import com.securevoting.crypto.HashUtil;
import com.securevoting.dto.VoterRollUploadResult;
import com.securevoting.entity.*;
import com.securevoting.exception.ResourceNotFoundException;
import com.securevoting.exception.ValidationException;
import com.securevoting.repository.ElectionRepository;
import com.securevoting.repository.VoterRecordRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class VoterRollService {

    private static final Logger log = LoggerFactory.getLogger(VoterRollService.class);

    private static final int MAX_ROWS = 50_000;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final ElectionRepository electionRepo;
    private final VoterRecordRepository recordRepo;
    private final AesEncryptionService aes;
    private final AppProperties props;

    public VoterRollService(ElectionRepository electionRepo,
                            VoterRecordRepository recordRepo,
                            AesEncryptionService aes,
                            AppProperties props) {
        this.electionRepo = electionRepo;
        this.recordRepo = recordRepo;
        this.aes = aes;
        this.props = props;
    }

    /**
     * Parse and persist a CSV voter roll for an election.
     * Replaces any existing voter roll (DRAFT-only).
     */
    @Transactional
    public VoterRollUploadResult upload(String electionCode, MultipartFile file, User actor) {
        Election e = electionRepo.findByElectionCode(electionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found: " + electionCode));

        if (!e.getCreator().getId().equals(actor.getId())) {
            throw new ValidationException("You are not the organizer of this election.");
        }
        if (e.getStatus() != ElectionStatus.DRAFT) {
            throw new ValidationException("Voter roll can only be uploaded while the election is in DRAFT.");
        }
        if (!e.isSchemaLocked()) {
            throw new ValidationException("Lock the voter schema before uploading the CSV.");
        }
        if (file == null || file.isEmpty()) {
            throw new ValidationException("Please choose a non-empty CSV file.");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new ValidationException("File too large (max 10 MB).");
        }

        // Build expected column set from locked schema
        List<VoterSchemaField> schema = e.getSchemaFields();

        Set<String> expectedKeys = schema.stream()
                .map(VoterSchemaField::getFieldKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // BUG FIX: identity fields must be ordered by displayOrder (NOT alphabetically by key)
        // to match VoteService.identifyVoter() which uses
        // findByElectionAndIdentityFieldTrueOrderByDisplayOrderAsc().
        List<String> identityKeys = schema.stream()
                .filter(VoterSchemaField::isIdentityField)
                .sorted(Comparator.comparingInt(VoterSchemaField::getDisplayOrder))
                .map(VoterSchemaField::getFieldKey)
                .toList();

        String contactKey = schema.stream()
                .filter(VoterSchemaField::isContactField)
                .map(VoterSchemaField::getFieldKey)
                .findFirst()
                .orElseThrow(() -> new ValidationException(
                        "Schema has no contact field — cannot send voting OTPs."));

        Map<String, VoterFieldType> fieldTypes = schema.stream()
                .collect(Collectors.toMap(
                        VoterSchemaField::getFieldKey,
                        VoterSchemaField::getFieldType));
        Map<String, Boolean> fieldRequired = schema.stream()
                .collect(Collectors.toMap(
                        VoterSchemaField::getFieldKey,
                        VoterSchemaField::isRequired));

        // Save the file
        String storedName = storeFile(electionCode, file);

        VoterRollUploadResult.Builder out = new VoterRollUploadResult.Builder(electionCode)
                .storedFileName(storedName);

        int wiped = (int) recordRepo.countByElection(e);
        recordRepo.deleteAllByElection(e);
        if (wiped > 0) {
            log.info("Wiped {} existing voter records for election {}", wiped, electionCode);
        }

        // Parse CSV
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .build();

        Set<String> seenIdentityHashes = new HashSet<>();
        int rowNum = 1; // header is row 1

        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVParser.parse(reader, format)) {

            // Header check (exact match, case-sensitive)
            Set<String> csvHeaders = parser.getHeaderMap().keySet();
            if (!csvHeaders.equals(expectedKeys)) {
                Set<String> missing = new LinkedHashSet<>(expectedKeys);
                missing.removeAll(csvHeaders);
                Set<String> extra = new LinkedHashSet<>(csvHeaders);
                extra.removeAll(expectedKeys);
                throw new ValidationException(
                        "CSV headers don't match the locked schema." +
                                (missing.isEmpty() ? "" : " Missing: " + missing) +
                                (extra.isEmpty()   ? "" : " Unexpected: " + extra));
            }

            for (CSVRecord record : parser) {
                rowNum++;
                int total = out.build().getTotalRowsRead() + 1;
                if (total > MAX_ROWS) {
                    out.reject(rowNum, "Row exceeds max " + MAX_ROWS + " — aborting.");
                    break;
                }
                processRow(e, record, schema, identityKeys, contactKey,
                        fieldTypes, fieldRequired, seenIdentityHashes, rowNum, out);
            }

            out.totalRowsRead(rowNum - 1);
            return out.build();

        } catch (ValidationException ve) {
            throw ve;
        } catch (IOException ex) {
            log.error("CSV parse failure for election {}", electionCode, ex);
            throw new ValidationException("Could not read CSV: " + ex.getMessage());
        }
    }

    private void processRow(Election e,
                            CSVRecord record,
                            List<VoterSchemaField> schema,
                            List<String> identityKeys,
                            String contactKey,
                            Map<String, VoterFieldType> fieldTypes,
                            Map<String, Boolean> fieldRequired,
                            Set<String> seenIdentityHashes,
                            int rowNum,
                            VoterRollUploadResult.Builder out) {

        Map<String, String> values = new LinkedHashMap<>();
        for (VoterSchemaField sf : schema) {
            String raw = record.isMapped(sf.getFieldKey()) ? record.get(sf.getFieldKey()) : "";
            values.put(sf.getFieldKey(), raw == null ? "" : raw.trim());
        }

        // Required-field + type validation
        for (VoterSchemaField sf : schema) {
            String key = sf.getFieldKey();
            String val = values.get(key);
            boolean blank = val == null || val.isEmpty();

            if (blank && Boolean.TRUE.equals(fieldRequired.get(key))) {
                out.reject(rowNum, "Required field '" + key + "' is blank.");
                return;
            }
            if (!blank && !matchesType(val, fieldTypes.get(key))) {
                out.reject(rowNum, "Field '" + key + "' is not a valid " + fieldTypes.get(key) + ".");
                return;
            }
        }

        // ─────────────────────────────────────────────────────────────
        // BUG FIX: Composite identity hash — MUST match VoteService.identifyVoter() exactly.
        // Canonical formula:
        //   sha256( "<salt>|" + value1 + "|" + value2 + ... )
        // where:
        //   - values are taken in displayOrder of their identity fields
        //   - each value is trimmed and lowercased (Locale.ROOT)
        // OLD (broken): "<key1>=<val1>|<key2>=<val2>|<SALT>" — preserved case, key prefix, salt at end
        // ─────────────────────────────────────────────────────────────
        StringJoiner sj = new StringJoiner("|");
        for (String key : identityKeys) {
            String v = values.get(key);
            sj.add(v == null ? "" : v.trim().toLowerCase(Locale.ROOT));
        }
        String identityHash = HashUtil.sha256Hex(e.getValidationSalt() + "|" + sj);

        if (!seenIdentityHashes.add(identityHash)) {
            out.reject(rowNum, "Duplicate identity within this CSV.");
            return;
        }
        if (recordRepo.existsByElectionAndIdentityHash(e, identityHash)) {
            out.reject(rowNum, "Voter with this identity already exists in the roll.");
            return;
        }

        // Build VoterRecord
        String contactEmail = values.get(contactKey);
        String contactHash = HashUtil.sha256Hex(contactEmail.toLowerCase(Locale.ROOT) + e.getValidationSalt());

        VoterRecord vr = new VoterRecord();
        vr.setElection(e);
        vr.setIdentityHash(identityHash);
        vr.setContactHash(contactHash);
        vr.setContactEncrypted(aes.encrypt(contactEmail));
        vr.setCsvRowNumber(rowNum);

        for (Map.Entry<String, String> entry : values.entrySet()) {
            VoterRecordValue v = new VoterRecordValue();
            v.setVoterRecord(vr);
            v.setFieldKey(entry.getKey());
            v.setValueHash(HashUtil.sha256Hex(entry.getValue() + e.getValidationSalt()));
            v.setValueEncrypted(aes.encrypt(entry.getValue()));
            vr.getValues().add(v);
        }

        recordRepo.save(vr);
        out.accepted();
    }

    private boolean matchesType(String value, VoterFieldType type) {
        return switch (type) {
            case TEXT   -> true;
            case NUMBER -> NUMBER_PATTERN.matcher(value).matches();
            case EMAIL  -> EMAIL_PATTERN.matcher(value).matches();
            case DATE   -> DATE_PATTERN.matcher(value).matches();
        };
    }

    private String storeFile(String electionCode, MultipartFile file) {
        try {
            Path dir = Paths.get(props.getUploads().getRoot(),
                    props.getUploads().getCsvSubdir(),
                    electionCode);
            Files.createDirectories(dir);

            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String safeName = (file.getOriginalFilename() == null ? "voter-roll.csv"
                    : file.getOriginalFilename().replaceAll("[^A-Za-z0-9._-]", "_"));
            Path target = dir.resolve(ts + "_" + safeName);

            file.transferTo(target);
            log.info("Stored voter roll CSV for {} at {}", electionCode, target);
            return target.getFileName().toString();
        } catch (IOException ex) {
            log.error("Could not save uploaded CSV", ex);
            throw new ValidationException("Could not save uploaded file: " + ex.getMessage());
        }
    }
}