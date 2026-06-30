package com.erp.ar.service;

import com.erp.ar.exception.UnbalancedJournalEntryException;
import com.erp.ar.model.JournalEntry;
import com.erp.ar.model.JournalEntryLine;
import com.erp.ar.model.SourceType;
import com.erp.ar.repository.JournalEntryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JournalEntryServiceTest {

    private final JournalEntryRepository repo = mock(JournalEntryRepository.class);
    private final JournalEntryService service = new JournalEntryService(repo);

    @Test
    void postsBalancedEntrySuccessfully() {
        UUID entityId = UUID.randomUUID();
        UUID arAccount = UUID.randomUUID();
        UUID revenueAccount = UUID.randomUUID();

        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        List<JournalEntryLine> lines = List.of(
            JournalEntryLine.debit(arAccount, new BigDecimal("1000.00")),
            JournalEntryLine.credit(revenueAccount, new BigDecimal("1000.00"))
        );

        JournalEntry result = service.post(entityId, SourceType.INVOICE_APPROVAL,
            UUID.randomUUID(), LocalDate.of(2026, 6, 15), UUID.randomUUID(), lines);

        assertNotNull(result);
        assertEquals("2026-06", result.getPostingPeriod());
        verify(repo, times(1)).save(any());
    }

    @Test
    void rejectsUnbalancedEntry() {
        UUID entityId = UUID.randomUUID();
        UUID arAccount = UUID.randomUUID();
        UUID revenueAccount = UUID.randomUUID();

        // Deliberately unbalanced: debit 1000, credit only 900
        List<JournalEntryLine> lines = List.of(
            JournalEntryLine.debit(arAccount, new BigDecimal("1000.00")),
            JournalEntryLine.credit(revenueAccount, new BigDecimal("900.00"))
        );

        assertThrows(UnbalancedJournalEntryException.class, () ->
            service.post(entityId, SourceType.INVOICE_APPROVAL,
                UUID.randomUUID(), LocalDate.of(2026, 6, 15), UUID.randomUUID(), lines));

        // Critically: nothing should be persisted if the entry doesn't balance
        verify(repo, never()).save(any());
    }

    @Test
    void reversalFlipsDebitsAndCredits() {
        UUID entityId = UUID.randomUUID();
        UUID arAccount = UUID.randomUUID();
        UUID revenueAccount = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();

        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        JournalEntry original = new JournalEntry();
        original.setJournalEntryId(UUID.randomUUID());
        original.setEntityId(entityId);
        original.setSourceId(sourceId);
        original.setLines(List.of(
            JournalEntryLine.debit(arAccount, new BigDecimal("500.00")),
            JournalEntryLine.credit(revenueAccount, new BigDecimal("500.00"))
        ));

        JournalEntry reversal = service.reverse(original, UUID.randomUUID(), LocalDate.of(2026, 6, 20));

        assertEquals(SourceType.REVERSAL, reversal.getSourceType());
        assertEquals(original.getJournalEntryId(), reversal.getReversesJournalEntryId());

        // original debit account should now be credited, and vice versa
        boolean arNowCredited = reversal.getLines().stream()
            .anyMatch(l -> l.getGlAccountId().equals(arAccount) && l.getCreditAmount().compareTo(new BigDecimal("500.00")) == 0);
        boolean revenueNowDebited = reversal.getLines().stream()
            .anyMatch(l -> l.getGlAccountId().equals(revenueAccount) && l.getDebitAmount().compareTo(new BigDecimal("500.00")) == 0);

        assertTrue(arNowCredited);
        assertTrue(revenueNowDebited);
    }
}
