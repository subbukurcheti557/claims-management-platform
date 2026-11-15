package com.icmp.claims.claim.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Liability;
import com.icmp.claims.claim.domain.RejectionReason;

/** Input commands for {@link ClaimCommandService}; decoupled from the web DTOs. */
public final class ClaimCommands {

    private ClaimCommands() {
    }

    public record SubmitClaim(String policyNumber, ClaimType claimType, LocalDate incidentDate,
                              String incidentLocation, String description, boolean injuryReported,
                              BigDecimal estimatedLoss) {
    }

    public record Assign(UUID claimId, Long expectedVersion, UUID officerId) {
    }

    public record RequestInfo(UUID claimId, Long expectedVersion, String question) {
    }

    public record RespondToInfo(UUID claimId, UUID requestId, String response) {
    }

    public record Assess(UUID claimId, Long expectedVersion, Liability liability, BigDecimal recommendedAmount,
                         BigDecimal reserveAmount, String findings) {
    }

    public record Approve(UUID claimId, Long expectedVersion, BigDecimal amount) {
    }

    public record Settle(UUID claimId, Long expectedVersion, String paymentReference) {
    }

    public record Reject(UUID claimId, Long expectedVersion, RejectionReason reason, String explanation) {
    }

    public record Withdraw(UUID claimId, String reason) {
    }

    public record AddNote(UUID claimId, String body) {
    }
}
