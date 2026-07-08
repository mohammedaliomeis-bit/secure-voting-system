package com.securevoting.controller;

import com.securevoting.service.CandidatePhotoService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/uploads")
public class UploadsController {

    private final CandidatePhotoService photoService;

    public UploadsController(CandidatePhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/candidates/{filename:.+}")
    public ResponseEntity<Resource> candidatePhoto(@PathVariable String filename) {
        Path safe = photoService.resolveSafe(filename);
        if (safe == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType type = mediaTypeFor(filename);
        Resource body = new FileSystemResource(safe);
        long len;
        try {
            len = Files.size(safe);
        } catch (Exception ex) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(len)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private static MediaType mediaTypeFor(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (lower.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        return MediaType.IMAGE_JPEG; // jpg / jpeg
    }
}