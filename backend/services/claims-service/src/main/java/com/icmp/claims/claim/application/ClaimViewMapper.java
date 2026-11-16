package com.icmp.claims.claim.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.icmp.claims.claim.application.view.ClaimViews.AssessmentView;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimDetail;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimSummary;
import com.icmp.claims.claim.application.view.ClaimViews.InfoRequestView;
import com.icmp.claims.claim.application.view.ClaimViews.NoteView;
import com.icmp.claims.claim.application.view.ClaimViews.TimelineEntry;
import com.icmp.claims.claim.domain.Assessment;
import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimNote;
import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.claim.domain.ClaimStatusHistory;
import com.icmp.claims.claim.domain.CurrencyConverter;
import com.icmp.claims.claim.domain.InfoRequest;

/** Maps aggregates to read models. Must be called inside a transaction (lazy collections). */
@Component
public class ClaimViewMapper {

    private final Clock clock;
    private final CurrencyConverter converter;

    public ClaimViewMapper(Clock clock, CurrencyConverter converter) {
        this.clock = clock;
        this.converter = converter;
    }

    public ClaimSummary summary(Claim c, boolean staffView) {
        Instant now = Instant.now(clock);
        return new ClaimSummary(c.getId(), c.getClaimNumber(), c.getPolicyNumber(), c.getClaimType().name(),
                c.getMarket().name(), c.getCurrency(), c.getStatus().name(), c.getIncidentDate(), c.getEstimatedLoss(),
                c.getSubmittedAt(), c.getUpdatedAt(), c.getAssignedOfficerName(), c.openInfoRequests().size(),
                c.getVersion(),
                staffView ? c.getClaimantName() : null,
                staffView ? c.getPriority().name() : null,
                staffView ? c.getReserveAmount() : null,
                staffView ? c.getSlaDueAt() : null,
                staffView ? c.isOverdue(now) : null,
                staffView ? c.getAssignedOfficerId() : null,
                staffView ? c.getPossibleDuplicateOf() : null);
    }

    public ClaimDetail detail(Claim c, boolean staffView, long noteCount) {
        Instant now = Instant.now(clock);
        List<AssessmentView> assessments = c.getAssessments().stream().map(ClaimViewMapper::assessment).toList();
        return new ClaimDetail(c.getId(), c.getClaimNumber(), c.getPolicyNumber(), c.getClaimType().name(),
                c.getMarket().name(), c.getCurrency(), c.getStatus().name(), allowedActions(c, staffView),
                c.getIncidentDate(), c.getIncidentLocation(), c.getDescription(), c.isInjuryReported(),
                c.getEstimatedLoss(), c.getInsuredItem(), c.getAssignedOfficerName(), c.getApprovedAmount(),
                c.getPaidAmount(), c.getPaymentReference(),
                c.getRejectionReason() == null ? null : c.getRejectionReason().name(),
                c.getRejectionReason() == null ? null : c.getRejectionReason().description(),
                c.getDecisionNote(), c.getSubmittedAt(), c.getUpdatedAt(), c.getClosedAt(), c.getVersion(),
                c.getInfoRequests().stream().map(ClaimViewMapper::infoRequest).toList(),
                staffView ? c.getClaimantId() : null,
                staffView ? c.getClaimantName() : null,
                staffView ? c.getAssignedOfficerId() : null,
                staffView ? c.getPriority().name() : null,
                staffView ? c.getSlaDueAt() : null,
                staffView ? c.isOverdue(now) : null,
                staffView ? c.getReserveAmount() : null,
                staffView ? c.getSumInsuredSnapshot() : null,
                staffView ? c.getDeductibleSnapshot() : null,
                staffView ? c.maxPayable() : null,
                staffView ? converter.toUsd(c.getEstimatedLoss(), c.getCurrency()) : null,
                staffView ? c.latestAssessment().map(ClaimViewMapper::assessment).orElse(null) : null,
                staffView ? assessments : null,
                staffView ? noteCount : null,
                staffView ? c.getPossibleDuplicateOf() : null);
    }

    public List<TimelineEntry> timeline(Claim c) {
        return c.getHistory().stream().map(ClaimViewMapper::timelineEntry).toList();
    }

    public NoteView note(ClaimNote n) {
        return new NoteView(n.getId(), n.getBody(), n.getAuthorName(), n.getAuthorId(), n.getCreatedAt());
    }

    /** Actions the UI may offer for the current status. The server still enforces every rule. */
    static List<String> allowedActions(Claim c, boolean staffView) {
        List<String> actions = new ArrayList<>();
        ClaimStatus s = c.getStatus();
        if (staffView) {
            if (s == ClaimStatus.SUBMITTED) {
                actions.add("ASSIGN");
            }
            if (s.isOpen() && s != ClaimStatus.SUBMITTED) {
                actions.add("REASSIGN");
            }
            if (s == ClaimStatus.UNDER_REVIEW || s == ClaimStatus.ASSESSED || s == ClaimStatus.INFO_REQUESTED) {
                actions.add("REQUEST_INFO");
            }
            if (s == ClaimStatus.UNDER_REVIEW || s == ClaimStatus.ASSESSED) {
                actions.add("ASSESS");
                actions.add("REJECT");
            }
            if (s == ClaimStatus.ASSESSED) {
                actions.add("APPROVE");
            }
            if (s == ClaimStatus.APPROVED) {
                actions.add("SETTLE");
            }
        } else {
            if (s == ClaimStatus.INFO_REQUESTED) {
                actions.add("RESPOND");
            }
            if (s.canTransitionTo(ClaimStatus.WITHDRAWN)) {
                actions.add("WITHDRAW");
            }
        }
        return actions;
    }

    private static InfoRequestView infoRequest(InfoRequest r) {
        return new InfoRequestView(r.getId(), r.getQuestion(), r.getResponse(), r.getStatus().name(),
                r.getRequestedByName(), r.getRequestedAt(), r.getRespondedAt());
    }

    private static AssessmentView assessment(Assessment a) {
        return new AssessmentView(a.getId(), a.getLiability().name(), a.getRecommendedAmount(),
                a.getReserveAmount(), a.getFindings(), a.getAssessorName(), a.getAssessedAt());
    }

    private static TimelineEntry timelineEntry(ClaimStatusHistory h) {
        return new TimelineEntry(h.getOccurredAt(), h.getFromStatus() == null ? null : h.getFromStatus().name(),
                h.getToStatus().name(), h.getActorName(), h.getActorRole(), h.getComment());
    }
}
