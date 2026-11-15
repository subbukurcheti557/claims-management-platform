package com.icmp.claims.claim.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.claim.domain.Priority;

/** Projection used by the dashboards; avoids loading full aggregates. */
public record ClaimFigures(Market market, String currency, ClaimType claimType, ClaimStatus status,
                           Priority priority, UUID assignedOfficerId, String assignedOfficerName,
                           BigDecimal reserveAmount, BigDecimal paidAmount, Instant submittedAt, Instant closedAt,
                           Instant slaDueAt) {

    public boolean isOverdue(Instant now) {
        return status.isOpen() && now.isAfter(slaDueAt);
    }
}
