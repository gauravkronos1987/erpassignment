package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "journal_entry_line")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntryLine {

    @Id
    @GeneratedValue
    @Column(name = "line_id")
    private UUID lineId;

    @Column(name = "gl_account_id", nullable = false)
    private UUID glAccountId;

    @Column(name = "debit_amount", nullable = false)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    public static JournalEntryLine debit(UUID glAccountId, BigDecimal amount) {
        JournalEntryLine line = new JournalEntryLine();
        line.setGlAccountId(glAccountId);
        line.setDebitAmount(amount);
        line.setCreditAmount(BigDecimal.ZERO);
        return line;
    }

    public static JournalEntryLine credit(UUID glAccountId, BigDecimal amount) {
        JournalEntryLine line = new JournalEntryLine();
        line.setGlAccountId(glAccountId);
        line.setCreditAmount(amount);
        line.setDebitAmount(BigDecimal.ZERO);
        return line;
    }
}
