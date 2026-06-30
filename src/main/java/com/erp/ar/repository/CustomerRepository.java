package com.erp.ar.repository;

import com.erp.ar.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByCustomerIdAndTenantId(UUID customerId, UUID tenantId);
}
