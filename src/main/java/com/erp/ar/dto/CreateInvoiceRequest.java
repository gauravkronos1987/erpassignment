package com.erp.ar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class CreateInvoiceRequest {

    @NotNull
    private UUID entityId;

    @NotNull
    private UUID customerId;

    @NotNull
    private LocalDate invoiceDate;

    @NotNull
    private String transactionCurrency;

    @NotNull
    private UUID createdBy;

    @NotEmpty
    @Valid
    private List<LineItemRequest> lineItems;

    @Getter
    @Setter
    public static class LineItemRequest {
        @NotNull
        private String description;
        @NotNull
        private BigDecimal quantity;
        @NotNull
        private BigDecimal unitPrice;
        @NotNull
        private UUID revenueGlAccountId;
        private boolean deferred = false;
    }
}
