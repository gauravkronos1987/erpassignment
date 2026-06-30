package com.erp.ar.controller;

import com.erp.ar.dto.JournalEntryResponse;
import com.erp.ar.model.SourceType;
import com.erp.ar.repository.JournalEntryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/journal-entries")
public class JournalEntryController {

    private final JournalEntryRepository journalEntryRepository;

    public JournalEntryController(JournalEntryRepository journalEntryRepository) {
        this.journalEntryRepository = journalEntryRepository;
    }

    /**
     * Returns the journal entries tracing back to a given invoice --
     * the direct audit path from invoice to GL, including any reversals
     * (which also carry source_id = the original invoice's id).
     */
    @GetMapping
    public ResponseEntity<List<JournalEntryResponse>> getByInvoice(
            @RequestParam("invoice") UUID invoiceId) {

        List<JournalEntryResponse> entries = journalEntryRepository
            .findBySourceTypeAndSourceId(SourceType.INVOICE_APPROVAL, invoiceId)
            .stream()
            .map(JournalEntryResponse::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(entries);
    }
}
