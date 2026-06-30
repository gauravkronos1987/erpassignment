package com.erp.ar.dto;

import com.erp.ar.model.JournalEntry;
import com.erp.ar.model.JournalEntryLine;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class JournalEntryResponse {
    private UUID journalEntryId;
    private String sourceType;
    private UUID sourceId;
    private LocalDate entryDate;
    private String postingPeriod;
    private List<LineResponse> lines;

    public static JournalEntryResponse from(JournalEntry je) {
        return JournalEntryResponse.builder()
            .journalEntryId(je.getJournalEntryId())
            .sourceType(je.getSourceType().name())
            .sourceId(je.getSourceId())
            .entryDate(je.getEntryDate())
            .postingPeriod(je.getPostingPeriod())
            .lines(je.getLines().stream().map(LineResponse::from).collect(Collectors.toList()))
            .build();
    }

    @Getter
    @Builder
    public static class LineResponse {
        private UUID glAccountId;
        private BigDecimal debitAmount;
        private BigDecimal creditAmount;

        public static LineResponse from(JournalEntryLine line) {
            return LineResponse.builder()
                .glAccountId(line.getGlAccountId())
                .debitAmount(line.getDebitAmount())
                .creditAmount(line.getCreditAmount())
                .build();
        }
    }
}
