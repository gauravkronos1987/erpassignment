package com.erp.ar.controller;

import com.erp.ar.dto.PaymentResponse;
import com.erp.ar.dto.RecordPaymentRequest;
import com.erp.ar.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> recordPayment(
            @RequestHeader("X-Tenant-Id") UUID tenantId,
            @Valid @RequestBody RecordPaymentRequest request) {
        PaymentResponse response = paymentService.recordPayment(tenantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
