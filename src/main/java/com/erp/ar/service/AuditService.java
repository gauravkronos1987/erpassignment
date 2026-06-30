package com.erp.ar.service;

import com.erp.ar.model.AuditLog;
import com.erp.ar.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(UUID tenantId, String entityType, UUID entityRecordId,
                     String action, String fieldName, String oldValue, String newValue,
                     UUID changedBy) {
        AuditLog entry = AuditLog.builder()
            .tenantId(tenantId)
            .entityType(entityType)
            .entityRecordId(entityRecordId)
            .action(action)
            .fieldName(fieldName)
            .oldValue(oldValue)
            .newValue(newValue)
            .changedBy(changedBy)
            .build();
        auditLogRepository.save(entry);
    }
}
