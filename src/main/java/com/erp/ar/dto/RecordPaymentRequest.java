package com.erp.ar.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RecordPaymentRequest {

    @NotNull
    private String idempotencyKey;

    @NotNull
    private UUID entityId;

    @NotNull
    private UUID customerId;

    @NotNull
    private BigDecimal amount;

    @NotNull
    private String paymentCurrency;

    @NotNull
    private LocalDate paymentDate;

    @NotNull
    private String paymentMethod;

    /** Optional. If omitted, oldest-invoice-first allocation is applied automatically. */
    @Valid
    private List<AllocationRequest> allocations;

    @Getter
    @Setter
    public static class AllocationRequest {
        @NotNull
        private UUID invoiceId;
        @NotNull
        private BigDecimal amount;
    }
}
