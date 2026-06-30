package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoice")
@Getter
@Setter
@NoArgsConstructor
public class Invoice {

    @Id
    @GeneratedValue
    @Column(name = "invoice_id")
    private UUID invoiceId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status = InvoiceStatus.DRAFT;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "transaction_currency", nullable = false, length = 3)
    private String transactionCurrency;

    @Column(name = "exchange_rate", nullable = false)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "amount_paid", nullable = false)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    /**
     * Optimistic concurrency control. JPA's native @Version annotation
     * gives us exactly the "version-checked write" behavior described
     * in the architecture doc -- a concurrent update against a stale
     * version throws OptimisticLockException automatically.
     */
    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "invoice_id")
    private List<InvoiceLineItem> lineItems = new ArrayList<>();

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Computed, not persisted -- always derived from total - paid. */
    @Transient
    public BigDecimal getAmountOutstanding() {
        return totalAmount.subtract(amountPaid);
    }
}
