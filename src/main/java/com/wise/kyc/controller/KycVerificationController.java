package com.wise.kyc.controller;

import com.wise.kyc.domain.repository.VerificationCaseRepository;
import com.wise.kyc.dto.InitiateVerificationRequest;
import com.wise.kyc.dto.VerificationCaseResponse;
import com.wise.kyc.service.KycVerificationOrchestrator;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/kyc/verifications")
public class KycVerificationController {

    private final KycVerificationOrchestrator orchestrator;
    private final VerificationCaseRepository caseRepository;

    public KycVerificationController(KycVerificationOrchestrator orchestrator,
                                     VerificationCaseRepository caseRepository) {
        this.orchestrator = orchestrator;
        this.caseRepository = caseRepository;
    }

    @PostMapping
    public ResponseEntity<VerificationCaseResponse> initiateVerification(
            @Valid @RequestBody InitiateVerificationRequest request) {
        VerificationCaseResponse response = orchestrator.initiateAndProcessVerification(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VerificationCaseResponse> getVerificationCase(@PathVariable String id) {
        return caseRepository.findById(id)
                .map(VerificationCaseResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<VerificationCaseResponse> getByCustomer(@PathVariable String customerId) {
        return caseRepository.findByCustomerId(customerId)
                .map(VerificationCaseResponse::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
