package com.icmp.claims.claim.domain.triage;

import java.math.BigDecimal;

import com.icmp.claims.claim.domain.ClaimType;

public record TriageInput(ClaimType claimType, BigDecimal estimatedLossUsd, boolean injuryReported,
                          String description) {
}
