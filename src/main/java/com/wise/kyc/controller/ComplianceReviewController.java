package com.wise.kyc.controller;

import com.wise.kyc.dto.ManualReviewDecisionRequest;
import com.wise.kyc.dto.VerificationCaseResponse;
import com.wise.kyc.service.HumanReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceReviewController {

    private final HumanReviewService reviewService;

    public ComplianceReviewController(HumanReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/queue")
    public ResponseEntity<List<VerificationCaseResponse>> getUnassignedQueue() {
        return ResponseEntity.ok(reviewService.getUnassignedReviewQueue());
    }

    @PostMapping("/cases/{caseId}/claim")
    public ResponseEntity<VerificationCaseResponse> claimCase(
            @PathVariable String caseId,
            @RequestParam String agentId) {
        return ResponseEntity.ok(reviewService.claimCase(caseId, agentId));
    }

    @PostMapping("/cases/{caseId}/decision")
    public ResponseEntity<VerificationCaseResponse> submitDecision(
            @PathVariable String caseId,
            @Valid @RequestBody ManualReviewDecisionRequest request) {
        return ResponseEntity.ok(reviewService.submitReviewDecision(caseId, request));
    }
}
