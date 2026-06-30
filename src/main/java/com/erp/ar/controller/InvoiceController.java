package com.erp.ar.controller;

import com.erp.ar.dto.CreateInvoiceRequest;
import com.erp.ar.dto.InvoiceResponse;
import com.erp.ar.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Multi-tenant isolation: every endpoint requires an X-Tenant-Id header.
 * In a production system this would be derived from the authenticated
 * JWT rather than trusted directly from a header -- the header approach
 * is used here purely to keep the prototype testable without a full auth
 * layer, and is called out explicitly as a simplification in the README.
 */
@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody CreateInvoiceRequest request) {
        InvoiceResponse response = invoiceService.createInvoice(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable("id") UUID invoiceId) {
        return ResponseEntity.ok(invoiceService.getInvoice(tenantId, invoiceId));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<InvoiceResponse> approveInvoice(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @PathVariable("id") UUID invoiceId,
            @RequestHeader("X-User-Id") UUID approvedBy) {
        return ResponseEntity.ok(invoiceService.approveInvoice(tenantId, invoiceId, approvedBy));
    }
}
