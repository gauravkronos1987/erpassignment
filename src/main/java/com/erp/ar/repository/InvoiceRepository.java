package com.erp.ar.repository;

import com.erp.ar.model.Invoice;
import com.erp.ar.model.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByInvoiceIdAndTenantId(UUID invoiceId, UUID tenantId);

    List<Invoice> findByTenantIdAndCustomerIdAndStatusNotIn(
            UUID tenantId, UUID customerId, List<InvoiceStatus> excludedStatuses);

    boolean existsByEntityIdAndInvoiceNumber(UUID entityId, String invoiceNumber);
}
