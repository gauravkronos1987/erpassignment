package com.erp.ar.service;

import com.erp.ar.exception.UnbalancedJournalEntryException;
import com.erp.ar.model.JournalEntry;
import com.erp.ar.model.JournalEntryLine;
import com.erp.ar.model.SourceType;
import com.erp.ar.repository.JournalEntryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Owns GL journal entry creation. This is intentionally the single
 * choke point through which every accounting-relevant action in the
 * system posts to the ledger -- invoice approval, payment allocation,
 * credit memo application all route through here. Centralising it
 * means the "debits must equal credits" invariant is enforced in
 * exactly one place rather than re-implemented per call site.
 */
@Service
public class JournalEntryService {

    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final JournalEntryRepository journalEntryRepository;

    public JournalEntryService(JournalEntryRepository journalEntryRepository) {
        this.journalEntryRepository = journalEntryRepository;
    }

    /**
     * Creates and persists a balanced journal entry.
     *
     * @throws UnbalancedJournalEntryException if sum(debits) != sum(credits).
     *         This should never trigger in practice if callers build lines
     *         correctly, but it's a deliberate last-line-of-defense check --
     *         an unbalanced entry reaching the GL is one of the worst possible
     *         bugs in a financial system, so we fail loudly rather than silently
     *         persist bad data.
     */
    public JournalEntry post(UUID entityId,
                              SourceType sourceType,
                              UUID sourceId,
                              LocalDate entryDate,
                              UUID createdBy,
                              List<JournalEntryLine> lines) {

        BigDecimal totalDebits = lines.stream()
            .map(JournalEntryLine::getDebitAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = lines.stream()
            .map(JournalEntryLine::getCreditAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new UnbalancedJournalEntryException(
                "Journal entry does not balance: debits=" + totalDebits + " credits=" + totalCredits
                + " (source=" + sourceType + " " + sourceId + ")");
        }

        JournalEntry entry = new JournalEntry();
        entry.setEntityId(entityId);
        entry.setSourceType(sourceType);
        entry.setSourceId(sourceId);
        entry.setEntryDate(entryDate);
        entry.setPostingPeriod(entryDate.format(PERIOD_FORMAT));
        entry.setCreatedBy(createdBy);
        entry.setLines(lines);

        return journalEntryRepository.save(entry);
    }

    /**
     * Reverses a previously posted journal entry by creating a new entry
     * with debit/credit flipped on every line. The original entry is never
     * mutated -- the GL is append-only, consistent with standard accounting
     * practice and our audit requirements.
     */
    public JournalEntry reverse(JournalEntry original, UUID createdBy, LocalDate reversalDate) {
        List<JournalEntryLine> reversedLines = original.getLines().stream()
            .map(line -> {
                if (line.getDebitAmount().compareTo(BigDecimal.ZERO) > 0) {
                    return JournalEntryLine.credit(line.getGlAccountId(), line.getDebitAmount());
                } else {
                    return JournalEntryLine.debit(line.getGlAccountId(), line.getCreditAmount());
                }
            })
            .toList();

        JournalEntry reversal = post(
            original.getEntityId(),
            SourceType.REVERSAL,
            original.getSourceId(),
            reversalDate,
            createdBy,
            reversedLines
        );
        reversal.setReversesJournalEntryId(original.getJournalEntryId());
        return journalEntryRepository.save(reversal);
    }
}
