package com.securevoting.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@RequiredArgsConstructor
public class VoterRollUploadResult {

    private final String electionCode;
    private final String storedFileName;
    private final int totalRowsRead;
    private final int acceptedCount;
    private final int rejectedCount;
    private final List<RowRejection> rejections;

    @Getter
    @RequiredArgsConstructor
    public static class RowRejection {
        private final int rowNumber;
        private final String reason;
    }

    public static class Builder {
        private final String electionCode;
        private String storedFileName;
        private int totalRowsRead;
        private int acceptedCount;
        private final List<RowRejection> rejections = new ArrayList<>();

        public Builder(String electionCode) { this.electionCode = electionCode; }

        public Builder storedFileName(String s) { this.storedFileName = s; return this; }
        public Builder totalRowsRead(int n) { this.totalRowsRead = n; return this; }
        public Builder accepted() { this.acceptedCount++; return this; }
        public Builder reject(int row, String reason) {
            rejections.add(new RowRejection(row, reason));
            return this;
        }

        public VoterRollUploadResult build() {
            return new VoterRollUploadResult(
                    electionCode, storedFileName, totalRowsRead,
                    acceptedCount, rejections.size(), List.copyOf(rejections));
        }
    }
}