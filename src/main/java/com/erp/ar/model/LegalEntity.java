package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Legal entity / subsidiary within a tenant. Each Entity keeps its own
 * functional currency and chart of accounts (GL) so its books always
 * balance independently for statutory reporting -- while still rolling
 * up under the parent Tenant for cross-entity / consolidated views.
 */
@Entity
@Table(name = "entity")
@Getter
@Setter
@NoArgsConstructor
public class LegalEntity {

    @Id
    @GeneratedValue
    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "parent_entity_id")
    private UUID parentEntityId;

    @Column(name = "legal_name", nullable = false)
    private String legalName;

    @Column(name = "functional_currency", nullable = false, length = 3)
    private String functionalCurrency;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
