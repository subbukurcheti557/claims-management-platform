package com.icmp.policy.policy.application;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icmp.policy.common.error.ApiException;
import com.icmp.policy.common.security.CurrentUser;
import com.icmp.policy.policy.domain.Policy;
import com.icmp.policy.policy.domain.Product;
import com.icmp.policy.policy.infrastructure.PolicyRepository;

@Service
@Transactional(readOnly = true)
public class PolicyQueryService {

    private final PolicyRepository policies;

    public PolicyQueryService(PolicyRepository policies) {
        this.policies = policies;
    }

    public List<Policy> myPolicies(CurrentUser user) {
        return policies.findByHolderUserIdOrderByPolicyNumber(user.id());
    }

    /** Claimants may only read their own policies; a foreign policy is reported as not found. */
    public Policy getVisibleTo(String policyNumber, CurrentUser user) {
        Policy policy = policies.findById(policyNumber).orElseThrow(() -> notFound(policyNumber));
        if (!user.isStaff() && !policy.isHeldBy(user.id())) {
            throw notFound(policyNumber);
        }
        return policy;
    }

    /**
     * Coverage check used synchronously by claims-service at FNOL. The policy must belong to the claimant
     * named in the request, and the caller must be that claimant or a staff member.
     */
    public CoverageResult checkCoverage(String policyNumber, java.util.UUID claimantId, LocalDate incidentDate,
                                        Product claimType, CurrentUser caller) {
        if (!caller.isStaff() && !caller.id().equals(claimantId)) {
            throw ApiException.forbidden("Coverage can only be checked for your own policies.");
        }
        Policy policy = policies.findById(policyNumber)
                .filter(p -> p.isHeldBy(claimantId))
                .orElseThrow(() -> notFound(policyNumber));
        return new CoverageResult(policy, policy.coverageProblems(incidentDate, claimType));
    }

    private static ApiException notFound(String policyNumber) {
        return ApiException.notFound("Policy " + policyNumber + " was not found.");
    }

    public record CoverageResult(Policy policy, List<String> problems) {
        public boolean covered() {
            return problems.isEmpty();
        }
    }
}
