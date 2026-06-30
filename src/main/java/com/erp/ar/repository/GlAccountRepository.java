package com.erp.ar.repository;

import com.erp.ar.model.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GlAccountRepository extends JpaRepository<GlAccount, UUID> {
    Optional<GlAccount> findByEntityIdAndAccountCode(UUID entityId, String accountCode);
    List<GlAccount> findByEntityId(UUID entityId);
}
