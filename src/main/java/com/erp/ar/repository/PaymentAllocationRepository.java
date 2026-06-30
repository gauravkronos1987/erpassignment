package com.erp.ar.repository;

import com.erp.ar.model.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
    List<PaymentAllocation> findByInvoiceId(UUID invoiceId);
    List<PaymentAllocation> findByPaymentId(UUID paymentId);
}
