package com.securevoting.service;

import com.securevoting.config.AppProperties;
import com.securevoting.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class CandidatePhotoService {

    private static final Logger log = LoggerFactory.getLogger(CandidatePhotoService.class);

    public static final long   MAX_BYTES     = 2L * 1024 * 1024; // 2 MB
    public static final Set<String> ALLOWED_EXT  =
            Set.of("jpg", "jpeg", "png", "webp");
    public static final Set<String> ALLOWED_MIME =
            Set.of("image/jpeg", "image/png", "image/webp");
    public static final Pattern STORED_NAME =
            Pattern.compile("^[a-f0-9-]{36}\\.(jpg|jpeg|png|webp)$");

    private final AppProperties props;
    private Path baseDir;

    public CandidatePhotoService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() throws IOException {
        Path root = Paths.get(props.getUploads().getRoot()).toAbsolutePath().normalize();
        this.baseDir = root.resolve(props.getUploads().getCandidatePhotoSubdir()).normalize();
        Files.createDirectories(baseDir);
        log.info("Candidate photo directory: {}", baseDir);
    }

    /**
     * Validates and stores a candidate photo.
     * @return the stored filename (e.g. "9a1b....png") — NEVER a path.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("No file uploaded.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ValidationException("Photo is larger than 2 MB.");
        }

        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ValidationException("Only JPG, JPEG, PNG, and WEBP files are allowed.");
        }

        String mime = Optional.ofNullable(file.getContentType())
                .map(String::toLowerCase).orElse("");
        if (!ALLOWED_MIME.contains(mime)) {
            throw new ValidationException("File MIME type is not a supported image.");
        }

        byte[] head = readHead(file, 12);
        String sniffed = sniffImage(head);
        if (sniffed == null) {
            throw new ValidationException("Uploaded file is not a valid image.");
        }
        // Cross-check sniffed type with extension/MIME
        if (!mimeMatches(sniffed, mime, ext)) {
            throw new ValidationException("File contents do not match its declared type.");
        }

        // Normalize ext for filename ("jpeg" stays "jpeg"; we never rewrite)
        String storedName = UUID.randomUUID() + "." + ext;
        Path target = baseDir.resolve(storedName).normalize();
        if (!target.startsWith(baseDir)) {
            // defense in depth — UUID + ext should never produce traversal
            throw new ValidationException("Invalid target path.");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            log.error("Failed to write candidate photo to {}", target, ex);
            throw new ValidationException("Could not save the uploaded photo.");
        }

        log.info("Stored candidate photo {} ({} bytes)", storedName, file.getSize());
        return storedName;
    }

    /** Deletes a stored photo. Safe to call with a null/blank value. */
    public void delete(String storedName) {
        if (storedName == null || storedName.isBlank()) return;
        if (!STORED_NAME.matcher(storedName).matches()) {
            log.warn("Refusing to delete photo with invalid name: {}", storedName);
            return;
        }
        try {
            Path target = baseDir.resolve(storedName).normalize();
            if (!target.startsWith(baseDir)) return;
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            log.warn("Could not delete candidate photo {}: {}", storedName, ex.getMessage());
        }
    }

    /**
     * Resolves a stored name to a safe, real path inside the base directory.
     * Returns null if the name is malformed, escapes the directory, or does not exist.
     */
    public Path resolveSafe(String storedName) {
        if (storedName == null || !STORED_NAME.matcher(storedName).matches()) return null;
        Path target = baseDir.resolve(storedName).normalize();
        if (!target.startsWith(baseDir)) return null;
        if (!Files.exists(target) || !Files.isRegularFile(target)) return null;
        return target;
    }

    public Path getBaseDir() { return baseDir; }

    // ---------- helpers ----------

    private static String extensionOf(String original) {
        if (original == null) return "";
        String name = original.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1);
        // strip anything that's not a safe alphanum
        return ext.replaceAll("[^a-z0-9]", "");
    }

    private static byte[] readHead(MultipartFile file, int n) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(n);
        } catch (IOException ex) {
            throw new ValidationException("Could not read uploaded file.");
        }
    }

    /** Returns "jpeg" | "png" | "webp" | null. */
    private static String sniffImage(byte[] b) {
        if (b == null) return null;
        // JPEG: FF D8 FF
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        // WEBP: "RIFF" .... "WEBP"
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "webp";
        }
        return null;
    }

    private static boolean mimeMatches(String sniffed, String mime, String ext) {
        return switch (sniffed) {
            case "jpeg" -> "image/jpeg".equals(mime) && (ext.equals("jpg") || ext.equals("jpeg"));
            case "png"  -> "image/png".equals(mime)  && ext.equals("png");
            case "webp" -> "image/webp".equals(mime) && ext.equals("webp");
            default     -> false;
        };
    }
}