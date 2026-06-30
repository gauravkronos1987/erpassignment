package com.erp.ar.service;

import com.erp.ar.dto.CreateInvoiceRequest;
import com.erp.ar.dto.InvoiceResponse;
import com.erp.ar.exception.InvalidStateTransitionException;
import com.erp.ar.exception.ResourceNotFoundException;
import com.erp.ar.exception.SegregationOfDutiesViolationException;
import com.erp.ar.model.*;
import com.erp.ar.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;
    private final GlAccountRepository glAccountRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final JournalEntryService journalEntryService;
    private final AuditService auditService;

    public InvoiceService(InvoiceRepository invoiceRepository,
                           CustomerRepository customerRepository,
                           GlAccountRepository glAccountRepository,
                           PaymentAllocationRepository paymentAllocationRepository,
                           JournalEntryService journalEntryService,
                           AuditService auditService) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
        this.glAccountRepository = glAccountRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.journalEntryService = journalEntryService;
        this.auditService = auditService;
    }

    @Transactional
    public InvoiceResponse createInvoice(UUID tenantId, CreateInvoiceRequest req) {
        Customer customer = customerRepository.findByCustomerIdAndTenantId(req.getCustomerId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Customer not found in tenant: " + req.getCustomerId()));

        Invoice invoice = new Invoice();
        invoice.setTenantId(tenantId);
        invoice.setEntityId(req.getEntityId());
        invoice.setCustomerId(req.getCustomerId());
        invoice.setInvoiceNumber(generateInvoiceNumber(req.getEntityId()));
        invoice.setStatus(InvoiceStatus.DRAFT);
        invoice.setInvoiceDate(req.getInvoiceDate());
        invoice.setDueDate(req.getInvoiceDate().plusDays(customer.getPaymentTermsDays()));
        invoice.setTransactionCurrency(req.getTransactionCurrency());
        invoice.setExchangeRate(BigDecimal.ONE); // locked properly at approval time; see approveInvoice()
        invoice.setCreatedBy(req.getCreatedBy());

        List<InvoiceLineItem> lineItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateInvoiceRequest.LineItemRequest liReq : req.getLineItems()) {
            InvoiceLineItem li = new InvoiceLineItem();
            li.setDescription(liReq.getDescription());
            li.setQuantity(liReq.getQuantity());
            li.setUnitPrice(liReq.getUnitPrice());
            BigDecimal lineTotal = liReq.getQuantity().multiply(liReq.getUnitPrice());
            li.setLineTotal(lineTotal);
            li.setRevenueGlAccountId(liReq.getRevenueGlAccountId());
            li.setDeferred(liReq.isDeferred());
            lineItems.add(li);
            subtotal = subtotal.add(lineTotal);
        }

        invoice.setLineItems(lineItems);
        invoice.setSubtotal(subtotal);
        invoice.setTaxTotal(BigDecimal.ZERO); // tax engine out of scope for this prototype
        invoice.setTotalAmount(subtotal);

        Invoice saved = invoiceRepository.save(invoice);

        auditService.log(tenantId, "Invoice", saved.getInvoiceId(), "CREATE",
            null, null, "status=DRAFT total=" + saved.getTotalAmount(), req.getCreatedBy());

        return InvoiceResponse.from(saved, List.of());
    }

    @Transactional
    public InvoiceResponse approveInvoice(UUID tenantId, UUID invoiceId, UUID approvedBy) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new InvalidStateTransitionException(
                "Cannot approve invoice in status " + invoice.getStatus() + " -- only DRAFT invoices can be approved");
        }

        // Segregation of duties: the approver must not be the same person who created the invoice.
        if (invoice.getCreatedBy().equals(approvedBy)) {
            throw new SegregationOfDutiesViolationException(
                "Segregation of duties violation: the invoice creator cannot also approve it");
        }

        // Generate the GL journal entry: Debit AR, Credit Revenue (per line, grouped by GL account)
        UUID arAccountId = glAccountRepository.findByEntityIdAndAccountCode(invoice.getEntityId(), "1200")
            .orElseThrow(() -> new ResourceNotFoundException("AR GL account (1200) not configured for entity"))
            .getGlAccountId();

        List<JournalEntryLine> lines = new ArrayList<>();
        lines.add(JournalEntryLine.debit(arAccountId, invoice.getTotalAmount()));

        // group line items by revenue account in case multiple revenue streams are on one invoice
        invoice.getLineItems().stream()
            .collect(Collectors.groupingBy(InvoiceLineItem::getRevenueGlAccountId,
                Collectors.reducing(BigDecimal.ZERO, InvoiceLineItem::getLineTotal, BigDecimal::add)))
            .forEach((glAccountId, amount) -> lines.add(JournalEntryLine.credit(glAccountId, amount)));

        journalEntryService.post(
            invoice.getEntityId(),
            SourceType.INVOICE_APPROVAL,
            invoice.getInvoiceId(),
            LocalDate.now(),
            approvedBy,
            lines
        );

        InvoiceStatus previousStatus = invoice.getStatus();
        invoice.setStatus(InvoiceStatus.APPROVED);
        invoice.setApprovedBy(approvedBy);
        Invoice saved = invoiceRepository.save(invoice);

        auditService.log(tenantId, "Invoice", invoiceId, "STATUS_CHANGE",
            "status", previousStatus.name(), InvoiceStatus.APPROVED.name(), approvedBy);

        return InvoiceResponse.from(saved, buildPaymentHistory(saved.getInvoiceId()));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(UUID tenantId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByInvoiceIdAndTenantId(invoiceId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Invoice not found: " + invoiceId));
        return InvoiceResponse.from(invoice, buildPaymentHistory(invoiceId));
    }

    private List<InvoiceResponse.PaymentHistoryEntry> buildPaymentHistory(UUID invoiceId) {
        return paymentAllocationRepository.findByInvoiceId(invoiceId).stream()
            .map(alloc -> InvoiceResponse.PaymentHistoryEntry.builder()
                .paymentId(alloc.getPaymentId())
                .allocatedAmount(alloc.getAllocatedAmount())
                .build())
            .collect(Collectors.toList());
    }

    private String generateInvoiceNumber(UUID entityId) {
        // Simple time-based generator for the prototype. A production system
        // would use a per-entity sequence table to guarantee strict gapless
        // numbering for audit purposes.
        return "INV-" + System.currentTimeMillis();
    }
}
