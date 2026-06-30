package com.erp.ar.service;

import com.erp.ar.dto.PaymentResponse;
import com.erp.ar.dto.RecordPaymentRequest;
import com.erp.ar.exception.ResourceNotFoundException;
import com.erp.ar.model.*;
import com.erp.ar.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Owns payment recording and allocation. Two things make this service
 * non-trivial compared to a generic "save a row" operation:
 *
 *  1. Idempotency -- payment recording is the operation most likely to be
 *     retried by a client after a network timeout, and double-recording a
 *     payment is one of the most damaging bugs a financial system can have.
 *     We check the client-supplied idempotency_key BEFORE doing any work.
 *
 *  2. Allocation -- a single payment can be split across multiple invoices,
 *     and a single invoice can receive multiple partial payments over time.
 *     Default allocation strategy (when the client doesn't specify it) is
 *     oldest-invoice-first, which is standard AR practice.
 */
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final InvoiceRepository invoiceRepository;
    private final GlAccountRepository glAccountRepository;
    private final JournalEntryService journalEntryService;
    private final AuditService auditService;

    public PaymentService(PaymentRepository paymentRepository,
                           PaymentAllocationRepository allocationRepository,
                           InvoiceRepository invoiceRepository,
                           GlAccountRepository glAccountRepository,
                           JournalEntryService journalEntryService,
                           AuditService auditService) {
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.invoiceRepository = invoiceRepository;
        this.glAccountRepository = glAccountRepository;
        this.journalEntryService = journalEntryService;
        this.auditService = auditService;
    }

    @Transactional
    public PaymentResponse recordPayment(UUID tenantId, RecordPaymentRequest req) {

        // ---- Idempotency check: must happen before any other work ----
        var existing = paymentRepository.findByIdempotencyKey(req.getIdempotencyKey());
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Payment payment = new Payment();
        payment.setTenantId(tenantId);
        payment.setEntityId(req.getEntityId());
        payment.setCustomerId(req.getCustomerId());
        payment.setPaymentDate(req.getPaymentDate());
        payment.setAmount(req.getAmount());
        payment.setPaymentCurrency(req.getPaymentCurrency());
        payment.setPaymentMethod(req.getPaymentMethod());
        payment.setIdempotencyKey(req.getIdempotencyKey());

        // ---- Determine allocation plan ----
        List<RecordPaymentRequest.AllocationRequest> allocationPlan;
        if (req.getAllocations() != null && !req.getAllocations().isEmpty()) {
            allocationPlan = req.getAllocations();
        } else {
            allocationPlan = buildOldestFirstAllocation(tenantId, req.getCustomerId(), req.getAmount());
        }

        BigDecimal totalAllocated = allocationPlan.stream()
            .map(RecordPaymentRequest.AllocationRequest::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal unallocated = req.getAmount().subtract(totalAllocated);
        payment.setUnallocatedAmount(unallocated);
        payment.setStatus(unallocated.compareTo(BigDecimal.ZERO) > 0
            ? PaymentStatus.PARTIALLY_ALLOCATED
            : PaymentStatus.ALLOCATED);

        Payment savedPayment = paymentRepository.save(payment);

        UUID cashAccountId = glAccountRepository.findByEntityIdAndAccountCode(req.getEntityId(), "1000")
            .orElseThrow(() -> new ResourceNotFoundException("Cash GL account (1000) not configured for entity"))
            .getGlAccountId();
        UUID arAccountId = glAccountRepository.findByEntityIdAndAccountCode(req.getEntityId(), "1200")
            .orElseThrow(() -> new ResourceNotFoundException("AR GL account (1200) not configured for entity"))
            .getGlAccountId();

        List<PaymentAllocation> allocations = new ArrayList<>();
        List<JournalEntryLine> journalLines = new ArrayList<>();

        // One debit to Cash for the full allocated amount, one credit to AR per invoice touched.
        if (totalAllocated.compareTo(BigDecimal.ZERO) > 0) {
            journalLines.add(JournalEntryLine.debit(cashAccountId, totalAllocated));
        }

        for (var allocReq : allocationPlan) {
            Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(allocReq.getInvoiceId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + allocReq.getInvoiceId()));

            PaymentAllocation alloc = new PaymentAllocation();
            alloc.setPaymentId(savedPayment.getPaymentId());
            alloc.setInvoiceId(invoice.getInvoiceId());
            alloc.setAllocatedAmount(allocReq.getAmount());
            allocations.add(alloc);

            journalLines.add(JournalEntryLine.credit(arAccountId, allocReq.getAmount()));

            // Update invoice running balance + status
            BigDecimal newAmountPaid = invoice.getAmountPaid().add(allocReq.getAmount());
            invoice.setAmountPaid(newAmountPaid);

            InvoiceStatus previousStatus = invoice.getStatus();
            if (newAmountPaid.compareTo(invoice.getTotalAmount()) >= 0) {
                invoice.setStatus(InvoiceStatus.PAID);
            } else {
                invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
            }
            invoiceRepository.save(invoice);

            if (previousStatus != invoice.getStatus()) {
                auditService.log(tenantId, "Invoice", invoice.getInvoiceId(), "STATUS_CHANGE",
                    "status", previousStatus.name(), invoice.getStatus().name(), null);
            }
        }

        allocationRepository.saveAll(allocations);

        if (!journalLines.isEmpty()) {
            journalEntryService.post(
                req.getEntityId(),
                SourceType.PAYMENT_RECEIPT,
                savedPayment.getPaymentId(),
                req.getPaymentDate(),
                null, // system-recorded; in a full implementation this would carry the recording user
                journalLines
            );
        }

        auditService.log(tenantId, "Payment", savedPayment.getPaymentId(), "CREATE",
            null, null, "amount=" + savedPayment.getAmount() + " allocated=" + totalAllocated, null);

        return toResponseWithAllocations(savedPayment, allocations);
    }

    /**
     * Default allocation: oldest invoice (by due_date) first, up to the
     * payment amount. Skips invoices already fully paid or void.
     */
    private List<RecordPaymentRequest.AllocationRequest> buildOldestFirstAllocation(
            UUID tenantId, UUID customerId, BigDecimal paymentAmount) {

        List<Invoice> outstandingInvoices = invoiceRepository
            .findByTenantIdAndCustomerIdAndStatusNotIn(tenantId, customerId,
                List.of(InvoiceStatus.PAID, InvoiceStatus.VOID, InvoiceStatus.DRAFT, InvoiceStatus.WRITTEN_OFF))
            .stream()
            .sorted(Comparator.comparing(Invoice::getDueDate))
            .toList();

        List<RecordPaymentRequest.AllocationRequest> plan = new ArrayList<>();
        BigDecimal remaining = paymentAmount;

        for (Invoice inv : outstandingInvoices) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal outstanding = inv.getAmountOutstanding();
            BigDecimal toApply = remaining.min(outstanding);

            if (toApply.compareTo(BigDecimal.ZERO) > 0) {
                var allocReq = new RecordPaymentRequest.AllocationRequest();
                allocReq.setInvoiceId(inv.getInvoiceId());
                allocReq.setAmount(toApply);
                plan.add(allocReq);
                remaining = remaining.subtract(toApply);
            }
        }
        return plan;
    }

    private PaymentResponse toResponse(Payment payment) {
        List<PaymentAllocation> allocs = allocationRepository.findByPaymentId(payment.getPaymentId());
        return toResponseWithAllocations(payment, allocs);
    }

    private PaymentResponse toResponseWithAllocations(Payment payment, List<PaymentAllocation> allocations) {
        return PaymentResponse.builder()
            .paymentId(payment.getPaymentId())
            .status(payment.getStatus().name())
            .amount(payment.getAmount())
            .unallocatedAmount(payment.getUnallocatedAmount())
            .paymentDate(payment.getPaymentDate())
            .allocations(allocations.stream()
                .map(a -> PaymentResponse.AllocationDetail.builder()
                    .invoiceId(a.getInvoiceId())
                    .allocatedAmount(a.getAllocatedAmount())
                    .build())
                .collect(Collectors.toList()))
            .build();
    }
}
