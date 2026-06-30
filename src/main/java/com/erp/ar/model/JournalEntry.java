package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entry")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry {

    @Id
    @GeneratedValue
    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    /** Polymorphic reference back to the Invoice/Payment/CreditMemo
     *  that generated this entry. */
    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "posting_period", nullable = false)
    private String postingPeriod;

    @Column(name = "reverses_journal_entry_id")
    private UUID reversesJournalEntryId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "journal_entry_id")
    private List<JournalEntryLine> lines = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
