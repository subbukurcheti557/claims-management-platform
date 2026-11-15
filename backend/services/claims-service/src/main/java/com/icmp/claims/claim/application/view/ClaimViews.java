package com.icmp.claims.claim.application.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Read models returned by the API. Staff-only fields (reserve, priority, SLA, coverage snapshot, assessments)
 * are null in the claimant view and omitted from the JSON.
 */
public final class ClaimViews {

    private ClaimViews() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimSummary(UUID id, String claimNumber, String policyNumber, String claimType, String market,
                               String currency, String status, LocalDate incidentDate, BigDecimal estimatedLoss,
                               Instant submittedAt, Instant updatedAt, String assignedOfficerName,
                               int openInfoRequests, long version,
                               // staff only
                               String claimantName, String priority, BigDecimal reserveAmount, Instant slaDueAt,
                               Boolean overdue, UUID assignedOfficerId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimDetail(UUID id, String claimNumber, String policyNumber, String claimType, String market,
                              String currency, String status, List<String> allowedActions, LocalDate incidentDate,
                              String incidentLocation, String description, boolean injuryReported,
                              BigDecimal estimatedLoss, String insuredItem, String assignedOfficerName,
                              BigDecimal approvedAmount, BigDecimal paidAmount, String paymentReference,
                              String rejectionReasonCode, String rejectionReason, String decisionNote,
                              Instant submittedAt, Instant updatedAt, Instant closedAt, long version,
                              List<InfoRequestView> infoRequests,
                              // staff only
                              UUID claimantId, String claimantName, UUID assignedOfficerId, String priority,
                              Instant slaDueAt, Boolean overdue, BigDecimal reserveAmount, BigDecimal sumInsured,
                              BigDecimal deductible, BigDecimal maxPayable, BigDecimal estimatedLossUsd,
                              AssessmentView latestAssessment, List<AssessmentView> assessments, Long noteCount) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InfoRequestView(UUID id, String question, String response, String status, String requestedByName,
                                  Instant requestedAt, Instant respondedAt) {
    }

    public record AssessmentView(UUID id, String liability, BigDecimal recommendedAmount, BigDecimal reserveAmount,
                                 String findings, String assessorName, Instant assessedAt) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEntry(Instant occurredAt, String fromStatus, String toStatus, String actorName,
                                String actorRole, String comment) {
    }

    public record NoteView(UUID id, String body, String authorName, UUID authorId, Instant createdAt) {
    }

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }
}
