package com.fintech.kyc.domain.repository;

import com.fintech.kyc.domain.model.VerificationCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VerificationCheckRepository extends JpaRepository<VerificationCheck, String> {
    List<VerificationCheck> findByVerificationCaseId(String caseId);
}
