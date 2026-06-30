package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue
    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "payment_currency", nullable = false, length = 3)
    private String paymentCurrency;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod;

    /** Client-supplied. Unique constraint at the DB level prevents duplicate
     *  recording if the client retries after a timeout. */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.RECORDED;

    @Column(name = "unallocated_amount", nullable = false)
    private BigDecimal unallocatedAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
