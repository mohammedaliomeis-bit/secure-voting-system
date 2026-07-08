package com.securevoting.dto;

import com.securevoting.entity.Election;

import java.time.Instant;
import java.util.List;

public record ElectionResults(
        Election election,
        int totalVotes,
        int eligibleVoters,
        double turnoutPercent,
        List<CandidateResult> results,
        List<CandidateResult> winners,
        boolean tie,
        int decryptionFailures,
        boolean chainValid,
        String chainMessage,
        Instant computedAt
) {
    public CandidateResult winner() {
        return winners.isEmpty() ? null : winners.get(0);
    }
}