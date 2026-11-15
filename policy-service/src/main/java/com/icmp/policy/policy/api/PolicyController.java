package com.icmp.policy.policy.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.policy.common.security.CurrentUser;
import com.icmp.policy.policy.application.PolicyQueryService;
import com.icmp.policy.policy.domain.Policy;
import com.icmp.policy.policy.domain.Product;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Policies")
public class PolicyController {

    private final PolicyQueryService service;

    public PolicyController(PolicyQueryService service) {
        this.service = service;
    }

    @GetMapping("/api/policies/mine")
    @PreAuthorize("hasRole('CLAIMANT')")
    @Operation(summary = "Policies held by the authenticated claimant")
    public List<PolicyResponse> mine() {
        return service.myPolicies(CurrentUser.get()).stream().map(PolicyResponse::from).toList();
    }

    @GetMapping("/api/policies/{policyNumber}")
    @Operation(summary = "One policy; claimants can only see their own")
    public PolicyResponse get(@PathVariable String policyNumber) {
        return PolicyResponse.from(service.getVisibleTo(policyNumber, CurrentUser.get()));
    }

    /** Service-to-service endpoint, blocked at the gateway. Called by claims-service during FNOL. */
    @GetMapping("/internal/policies/{policyNumber}/coverage")
    @Operation(summary = "Internal: is this incident covered by the policy?")
    public CoverageResponse coverage(@PathVariable String policyNumber,
                                     @RequestParam UUID claimantId,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentDate,
                                     @RequestParam Product claimType) {
        PolicyQueryService.CoverageResult result =
                service.checkCoverage(policyNumber, claimantId, incidentDate, claimType, CurrentUser.get());
        Policy p = result.policy();
        return new CoverageResponse(p.getPolicyNumber(), result.covered(), result.problems(), p.getProduct().name(),
                p.getMarket(), p.getCurrency(), p.getSumInsured(), p.getDeductible(), p.remainingSumInsured(),
                p.getHolderName(), p.getInsuredItem());
    }

    public record CoverageResponse(String policyNumber, boolean covered, List<String> reasons, String product,
                                   String market, String currency, BigDecimal sumInsured, BigDecimal deductible,
                                   BigDecimal remainingSumInsured, String holderName, String insuredItem) {
    }
}
