package com.icmp.claims.claim.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Liability;
import com.icmp.claims.claim.domain.RejectionReason;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Request bodies. Bean Validation covers shape; business rules are enforced by the domain. */
public final class ClaimRequests {

    private ClaimRequests() {
    }

    public record SubmitClaimRequest(
            @NotBlank @Size(max = 30) String policyNumber,
            @NotNull ClaimType claimType,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @NotBlank @Size(max = 300) String incidentLocation,
            @NotBlank @Size(min = 20, max = 4000, message = "must be between 20 and 4000 characters") String description,
            boolean injuryReported,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal estimatedLoss) {
    }

    public record AssignRequest(@PositiveOrZero Long expectedVersion, UUID officerId) {
    }

    public record InfoRequestRequest(@PositiveOrZero Long expectedVersion,
                                     @NotBlank @Size(min = 5, max = 2000) String question) {
    }

    public record InfoResponseRequest(@NotBlank @Size(max = 4000) String response) {
    }

    public record AssessmentRequest(
            @PositiveOrZero Long expectedVersion,
            @NotNull Liability liability,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal recommendedAmount,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal reserveAmount,
            @NotBlank @Size(min = 10, max = 4000) String findings) {
    }

    public record ApproveRequest(@PositiveOrZero Long expectedVersion,
                                 @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {
    }

    public record SettleRequest(@PositiveOrZero Long expectedVersion,
                                @NotBlank @Size(max = 60) String paymentReference) {
    }

    public record RejectRequest(@PositiveOrZero Long expectedVersion, @NotNull RejectionReason reasonCode,
                                @NotBlank @Size(min = 10, max = 2000) String explanation) {
    }

    public record WithdrawRequest(@Size(max = 1000) String reason) {
    }

    public record NoteRequest(@NotBlank @Size(max = 4000) String body) {
    }
}
