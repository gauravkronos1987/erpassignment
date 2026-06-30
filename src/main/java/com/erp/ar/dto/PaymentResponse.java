package com.erp.ar.dto;

import com.erp.ar.model.Payment;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class PaymentResponse {
    private UUID paymentId;
    private String status;
    private BigDecimal amount;
    private BigDecimal unallocatedAmount;
    private LocalDate paymentDate;
    private List<AllocationDetail> allocations;

    @Getter
    @Builder
    public static class AllocationDetail {
        private UUID invoiceId;
        private BigDecimal allocatedAmount;
    }
}
