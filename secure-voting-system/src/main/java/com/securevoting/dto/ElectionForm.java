package com.securevoting.dto;

import com.securevoting.entity.Election;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.time.ZoneId;

public class ElectionForm {

    @NotBlank
    @Size(min = 3, max = 150)
    private String title;

    @Size(max = 2000)
    private String description;

    @NotNull
    @Future
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startTime;

    @NotNull
    @Future
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endTime;

    @Min(1)
    @Max(20)
    private Integer maxVotesPerVoter = 1;

    public ElectionForm() {}

    /** Seeds the form from an existing election (used on the manage page). */
    public static ElectionForm from(Election e) {
        ElectionForm f = new ElectionForm();
        f.setTitle(e.getTitle());
        f.setDescription(e.getDescription());
        ZoneId zone = ZoneId.systemDefault();
        if (e.getStartTime() != null) {
            f.setStartTime(e.getStartTime().atZone(zone).toLocalDateTime());
        }
        if (e.getEndTime() != null) {
            f.setEndTime(e.getEndTime().atZone(zone).toLocalDateTime());
        }
        f.setMaxVotesPerVoter(e.getMaxVotesPerVoter());
        return f;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Integer getMaxVotesPerVoter() { return maxVotesPerVoter; }
    public void setMaxVotesPerVoter(Integer maxVotesPerVoter) { this.maxVotesPerVoter = maxVotesPerVoter; }
}