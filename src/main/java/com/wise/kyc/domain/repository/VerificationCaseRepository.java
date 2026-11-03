package com.wise.kyc.domain.repository;

import com.wise.kyc.domain.model.Jurisdiction;
import com.wise.kyc.domain.model.VerificationCase;
import com.wise.kyc.domain.model.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VerificationCaseRepository extends JpaRepository<VerificationCase, String> {
    Optional<VerificationCase> findByCustomerId(String customerId);
    List<VerificationCase> findByStatus(VerificationStatus status);
    List<VerificationCase> findByJurisdiction(Jurisdiction jurisdiction);
    List<VerificationCase> findByStatusAndAssignedAgentIdIsNull(VerificationStatus status);
}
