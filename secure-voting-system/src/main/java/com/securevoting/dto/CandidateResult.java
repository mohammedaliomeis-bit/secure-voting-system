package com.securevoting.dto;

public record CandidateResult(
        Long candidateId,
        String name,
        String party,
        int votes,
        double percent,
        int rank
) {}