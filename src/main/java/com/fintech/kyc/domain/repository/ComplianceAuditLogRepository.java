package com.fintech.kyc.domain.repository;

import com.fintech.kyc.domain.model.ComplianceAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceAuditLogRepository extends JpaRepository<ComplianceAuditLog, String> {
    List<ComplianceAuditLog> findByCaseIdOrderByTimestampDesc(String caseId);
}
