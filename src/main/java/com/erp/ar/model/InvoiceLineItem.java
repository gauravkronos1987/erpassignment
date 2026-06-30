package com.erp.ar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_line_item")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceLineItem {

    @Id
    @GeneratedValue
    @Column(name = "line_item_id")
    private UUID lineItemId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;

    @Column(name = "revenue_gl_account_id", nullable = false)
    private UUID revenueGlAccountId;

    @Column(nullable = false)
    private boolean deferred = false;
}
