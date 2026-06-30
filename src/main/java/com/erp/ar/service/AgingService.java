package com.erp.ar.service;

import com.erp.ar.dto.AgingResponse;
import com.erp.ar.model.Invoice;
import com.erp.ar.model.InvoiceStatus;
import com.erp.ar.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class AgingService {

    private final InvoiceRepository invoiceRepository;

    public AgingService(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = invoiceRepository;
    }

    @Transactional(readOnly = true)
    public AgingResponse computeAging(UUID tenantId, UUID customerId, LocalDate asOfDate) {

        List<Invoice> outstandingInvoices = invoiceRepository
            .findByTenantIdAndCustomerIdAndStatusNotIn(tenantId, customerId,
                List.of(InvoiceStatus.PAID, InvoiceStatus.VOID, InvoiceStatus.DRAFT));

        BigDecimal current = BigDecimal.ZERO;
        BigDecimal days1To30 = BigDecimal.ZERO;
        BigDecimal days31To60 = BigDecimal.ZERO;
        BigDecimal days61To90 = BigDecimal.ZERO;
        BigDecimal days90Plus = BigDecimal.ZERO;

        for (Invoice inv : outstandingInvoices) {
            BigDecimal outstanding = inv.getAmountOutstanding();
            if (outstanding.compareTo(BigDecimal.ZERO) <= 0) continue;

            long daysPastDue = ChronoUnit.DAYS.between(inv.getDueDate(), asOfDate);

            if (daysPastDue <= 0) {
                current = current.add(outstanding);
            } else if (daysPastDue <= 30) {
                days1To30 = days1To30.add(outstanding);
            } else if (daysPastDue <= 60) {
                days31To60 = days31To60.add(outstanding);
            } else if (daysPastDue <= 90) {
                days61To90 = days61To90.add(outstanding);
            } else {
                days90Plus = days90Plus.add(outstanding);
            }
        }

        BigDecimal total = current.add(days1To30).add(days31To60).add(days61To90).add(days90Plus);

        return AgingResponse.builder()
            .customerId(customerId)
            .asOfDate(asOfDate)
            .current(current)
            .days1To30(days1To30)
            .days31To60(days31To60)
            .days61To90(days61To90)
            .days90Plus(days90Plus)
            .totalOutstanding(total)
            .build();
    }
}
