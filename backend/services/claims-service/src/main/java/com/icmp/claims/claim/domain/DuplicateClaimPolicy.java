package com.icmp.claims.claim.domain;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Decides what happens when a claim is reported for a policy and incident date that already has claims.
 * <ul>
 *   <li>Another claim for that incident is still open: refuse (409). The incident is already being handled.</li>
 *   <li>Another claim for that incident was paid: accept, but flag the new claim as a possible duplicate so an
 *       officer checks it. A vehicle can genuinely be damaged twice on one day, so this is not a hard block.</li>
 *   <li>Rejected or withdrawn claims are ignored: the claimant may report the incident again.</li>
 * </ul>
 */
public final class DuplicateClaimPolicy {

    private DuplicateClaimPolicy() {
    }

    /**
     * @param sameIncident claims already recorded on the same policy with the same incident date
     * @return the claim number of a paid claim the new one may duplicate, or empty when there is none
     * @throws ClaimRuleException ({@link ClaimRuleException.Kind#DUPLICATE_CLAIM}) when one is still open
     */
    public static Optional<String> check(String policyNumber, LocalDate incidentDate, List<Claim> sameIncident) {
        sameIncident.stream()
                .filter(c -> c.getStatus().isOpen())
                .findFirst()
                .ifPresent(open -> {
                    throw new ClaimRuleException(ClaimRuleException.Kind.DUPLICATE_CLAIM,
                            "Claim " + open.getClaimNumber() + " for policy " + policyNumber + " and incident date "
                                    + incidentDate + " is already being handled (status " + open.getStatus()
                                    + "). Please follow that claim instead of reporting the incident again.");
                });
        return sameIncident.stream()
                .filter(c -> c.getStatus() == ClaimStatus.SETTLED)
                .max(Comparator.comparing(Claim::getSubmittedAt))
                .map(Claim::getClaimNumber);
    }
}
