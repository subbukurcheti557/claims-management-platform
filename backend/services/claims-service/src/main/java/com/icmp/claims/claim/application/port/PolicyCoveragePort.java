package com.icmp.claims.claim.application.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.icmp.claims.claim.domain.ClaimType;

/** Asks the policy system whether an incident is covered. Implemented by a REST adapter to policy-service. */
public interface PolicyCoveragePort {

    /** @return empty when the policy does not exist or does not belong to the claimant. */
    Optional<Coverage> checkCoverage(String policyNumber, UUID claimantId, LocalDate incidentDate,
                                     ClaimType claimType, String bearerToken);

    record Coverage(String policyNumber, boolean covered, List<String> reasons, String market, String currency,
                    BigDecimal sumInsured, BigDecimal deductible, BigDecimal remainingSumInsured, String insuredItem) {
    }
}
