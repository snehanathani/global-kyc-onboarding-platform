package com.fintech.kyc.controller;

import com.fintech.kyc.resilience.PrimaryIdentityVendor;
import com.fintech.kyc.rules.KycRule;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class SimulationAndAdminController {

    private final PrimaryIdentityVendor primaryVendor;
    private final List<KycRule> rules;

    public SimulationAndAdminController(PrimaryIdentityVendor primaryVendor, List<KycRule> rules) {
        this.primaryVendor = primaryVendor;
        this.rules = rules;
    }

    @PostMapping("/vendors/primary/outage")
    public ResponseEntity<Map<String, Object>> toggleOutage(@RequestParam boolean simulate) {
        primaryVendor.setSimulateOutage(simulate);
        return ResponseEntity.ok(Map.of(
                "primaryVendorOutageSimulated", simulate,
                "message", simulate ?
                        "Primary identity vendor (Onfido) simulated outage ACTIVATED. Resilience4j circuit breaker will trigger and fail over to Veriff." :
                        "Primary identity vendor restored to NORMAL operations."
        ));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<Map<String, Object>>> getRules() {
        List<Map<String, Object>> summary = rules.stream()
                .map(r -> Map.<String, Object>of(
                        "ruleId", r.getRuleId(),
                        "priority", r.getPriority(),
                        "description", r.getDescription(),
                        "shadowMode", r.isShadowMode()
                ))
                .toList();
        return ResponseEntity.ok(summary);
    }
}
