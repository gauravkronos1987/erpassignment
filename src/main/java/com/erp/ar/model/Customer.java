package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private String name;

    @Column(name = "billing_currency", nullable = false, length = 3)
    private String billingCurrency;

    @Column(name = "payment_terms_days", nullable = false)
    private Integer paymentTermsDays = 30;

    @Column(name = "credit_limit")
    private BigDecimal creditLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
