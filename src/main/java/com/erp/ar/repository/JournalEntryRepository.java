package com.erp.ar.repository;

import com.erp.ar.model.JournalEntry;
import com.erp.ar.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    List<JournalEntry> findBySourceTypeAndSourceId(SourceType sourceType, UUID sourceId);
}
