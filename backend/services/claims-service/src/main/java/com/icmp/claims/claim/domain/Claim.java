package com.icmp.claims.claim.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * The claim aggregate. All state changes go through intent methods (assign, requestInformation, assess, approve,
 * settle, reject, withdraw) that enforce the state machine and role rules, write the audit trail and record a
 * domain event. Nothing outside this class changes the status.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    private UUID id;

    @Column(name = "claim_number", nullable = false, unique = true)
    private String claimNumber;

    @Column(name = "policy_number", nullable = false)
    private String policyNumber;

    @Column(name = "claimant_id", nullable = false)
    private UUID claimantId;

    @Column(name = "claimant_name", nullable = false)
    private String claimantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", nullable = false)
    private ClaimType claimType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Column(nullable = false)
    private String currency;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_location", nullable = false)
    private String incidentLocation;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(name = "injury_reported", nullable = false)
    private boolean injuryReported;

    @Column(name = "estimated_loss", nullable = false)
    private BigDecimal estimatedLoss;

    @Column(name = "sum_insured_snapshot", nullable = false)
    private BigDecimal sumInsuredSnapshot;

    @Column(name = "deductible_snapshot", nullable = false)
    private BigDecimal deductibleSnapshot;

    /** The policy's full sum insured: the most that all claims on the policy together may pay. */
    @Column(name = "policy_sum_insured", nullable = false)
    private BigDecimal policySumInsured;

    /** Set when an already paid claim exists for the same policy and incident date. Staff only. */
    @Column(name = "possible_duplicate_of")
    private String possibleDuplicateOf;

    @Column(name = "insured_item")
    private String insuredItem;

    @Column(name = "reserve_amount", nullable = false)
    private BigDecimal reserveAmount;

    @Column(name = "approved_amount")
    private BigDecimal approvedAmount;

    @Column(name = "paid_amount")
    private BigDecimal paidAmount;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @Column(name = "assigned_officer_id")
    private UUID assignedOfficerId;

    @Column(name = "assigned_officer_name")
    private String assignedOfficerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason_code")
    private RejectionReason rejectionReason;

    @Column(name = "decision_note", length = 2000)
    private String decisionNote;

    @Column(name = "sla_due_at", nullable = false)
    private Instant slaDueAt;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC")
    private List<ClaimStatusHistory> history = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("requestedAt ASC")
    private List<InfoRequest> infoRequests = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("assessedAt ASC")
    private List<Assessment> assessments = new ArrayList<>();

    @Transient
    private final List<ClaimDomainEvent> pendingEvents = new ArrayList<>();

    protected Claim() {
        // JPA
    }

    // ------------------------------------------------------------------ FNOL

    /** First notice of loss: creates a SUBMITTED claim from a coverage-checked report. */
    public static Claim submit(String claimNumber, NewClaim report, CoverageSnapshot coverage, Priority priority,
                               Actor claimant, Instant now) {
        return submit(claimNumber, report, coverage, priority, claimant, now, null);
    }

    /**
     * As {@link #submit(String, NewClaim, CoverageSnapshot, Priority, Actor, Instant)}, for a claim that may repeat
     * an already paid claim ({@link DuplicateClaimPolicy}). Such a claim is flagged and triaged HIGH, so an officer
     * looks at it within the shortest SLA.
     */
    public static Claim submit(String claimNumber, NewClaim report, CoverageSnapshot coverage, Priority priority,
                               Actor claimant, Instant now, String possibleDuplicateOf) {
        if (possibleDuplicateOf != null) {
            priority = Priority.HIGH;
        }
        if (!claimant.isClaimant()) {
            throw ClaimRuleException.notPermitted("Only claimants can report a claim.");
        }
        if (report.incidentDate().isAfter(LocalDate.now())) {
            throw ClaimRuleException.rule("The incident date cannot be in the future.");
        }
        if (report.estimatedLoss().signum() <= 0) {
            throw ClaimRuleException.rule("The estimated loss must be greater than zero.");
        }
        Claim c = new Claim();
        c.id = UUID.randomUUID();
        c.claimNumber = claimNumber;
        c.policyNumber = report.policyNumber();
        c.claimantId = claimant.id();
        c.claimantName = claimant.name();
        c.claimType = report.claimType();
        c.market = coverage.market();
        c.currency = coverage.currency();
        c.incidentDate = report.incidentDate();
        c.incidentLocation = report.incidentLocation();
        c.description = report.description();
        c.injuryReported = report.injuryReported();
        c.estimatedLoss = report.estimatedLoss();
        c.sumInsuredSnapshot = coverage.remainingSumInsured();
        c.deductibleSnapshot = coverage.deductible();
        c.policySumInsured = coverage.policySumInsured();
        c.possibleDuplicateOf = possibleDuplicateOf;
        c.insuredItem = coverage.insuredItem();
        c.reserveAmount = initialReserve(report.estimatedLoss(), coverage.remainingSumInsured(), coverage.deductible());
        c.status = ClaimStatus.SUBMITTED;
        c.priority = priority;
        c.submittedAt = now;
        c.updatedAt = now;
        c.slaDueAt = now.plus(priority.sla());
        c.history.add(new ClaimStatusHistory(c, null, ClaimStatus.SUBMITTED, claimant, "Claim reported online", now));
        c.record(ClaimDomainEvent.SUBMITTED, claimant, data(
                "claimantName", c.claimantName,
                "claimType", c.claimType.name(),
                "estimatedLoss", c.estimatedLoss,
                "priority", c.priority.name()));
        return c;
    }

    /** Initial reserve = min(estimated loss, remaining sum insured) minus deductible, never negative. */
    public static BigDecimal initialReserve(BigDecimal estimatedLoss, BigDecimal remainingSumInsured,
                                            BigDecimal deductible) {
        return estimatedLoss.min(remainingSumInsured).subtract(deductible).max(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------ Assignment

    /**
     * Pick up (officer assigns to self) or assign (manager assigns anyone). A SUBMITTED claim moves to
     * UNDER_REVIEW; an open claim that is already assigned can only be reassigned by a manager.
     */
    public void assign(UUID officerId, String officerName, Actor actor, Instant now) {
        requireStaff(actor);
        if (status == ClaimStatus.SUBMITTED) {
            if (!actor.isManager() && !actor.id().equals(officerId)) {
                throw ClaimRuleException.notPermitted("Claims officers can only pick up claims for themselves.");
            }
            setOfficer(officerId, officerName);
            transition(ClaimStatus.UNDER_REVIEW, actor, "Assigned to " + officerName, "assign", now);
            record(ClaimDomainEvent.ASSIGNED, actor, data("officerId", officerId, "officerName", officerName,
                    "reassigned", false));
            return;
        }
        if (status.isClosed()) {
            throw new ClaimRuleException(ClaimRuleException.Kind.INVALID_TRANSITION,
                    "Cannot assign a claim that is " + status + ".");
        }
        if (officerId.equals(assignedOfficerId)) {
            throw ClaimRuleException.rule("The claim is already assigned to " + officerName + ".");
        }
        if (!actor.isManager()) {
            throw new ClaimRuleException(ClaimRuleException.Kind.INVALID_TRANSITION,
                    "This claim is already assigned to " + assignedOfficerName
                            + ". Only a claims manager can reassign it.");
        }
        String previous = assignedOfficerName;
        setOfficer(officerId, officerName);
        history.add(new ClaimStatusHistory(this, status, status, actor,
                "Reassigned from " + previous + " to " + officerName, now));
        touch(now);
        record(ClaimDomainEvent.ASSIGNED, actor, data("officerId", officerId, "officerName", officerName,
                "reassigned", true));
    }

    // ------------------------------------------------------------------ Information requests

    public InfoRequest requestInformation(String question, Actor actor, Instant now) {
        requireHandler(actor, "request information");
        if (status != ClaimStatus.INFO_REQUESTED) {
            transition(ClaimStatus.INFO_REQUESTED, actor, "Information requested: " + question,
                    "request information on", now);
        } else {
            touch(now);
        }
        InfoRequest request = new InfoRequest(this, question, actor, now);
        infoRequests.add(request);
        record(ClaimDomainEvent.INFO_REQUESTED, actor, data("requestId", request.getId(), "question", question));
        return request;
    }

    public void respondToInformationRequest(UUID requestId, String response, Actor actor, Instant now) {
        requireOwner(actor);
        InfoRequest request = infoRequests.stream().filter(r -> r.getId().equals(requestId)).findFirst()
                .orElseThrow(() -> ClaimRuleException.rule("Information request " + requestId
                        + " does not belong to this claim."));
        if (status != ClaimStatus.INFO_REQUESTED) {
            throw new ClaimRuleException(ClaimRuleException.Kind.INVALID_TRANSITION,
                    "This claim is not waiting for information (status " + status + ").");
        }
        request.answer(response, now);
        if (openInfoRequests().isEmpty()) {
            transition(ClaimStatus.UNDER_REVIEW, actor, "Claimant provided the requested information",
                    "respond to", now);
        } else {
            touch(now);
        }
        record(ClaimDomainEvent.INFO_PROVIDED, actor, data("requestId", requestId,
                "remainingOpenRequests", openInfoRequests().size()));
    }

    // ------------------------------------------------------------------ Assessment & decisions

    public Assessment assess(Liability liability, BigDecimal recommendedAmount, BigDecimal reserve, String findings,
                             Actor actor, Instant now) {
        requireHandler(actor, "assess");
        requireNonNegative(recommendedAmount, "recommended amount");
        requireNonNegative(reserve, "reserve");
        if (liability == Liability.DENIED && recommendedAmount.signum() != 0) {
            throw ClaimRuleException.rule("When liability is denied the recommended amount must be 0.");
        }
        requireWithinCover(recommendedAmount, "recommended amount");
        requireWithinCover(reserve, "reserve");
        boolean reassessment = status == ClaimStatus.ASSESSED;
        transition(ClaimStatus.ASSESSED, actor, (reassessment ? "Re-assessed: " : "Assessed: ") + liability
                + ", recommended " + currency + " " + recommendedAmount.toPlainString(), "assess", now);
        Assessment assessment = new Assessment(this, liability, recommendedAmount, reserve, findings, actor, now);
        assessments.add(assessment);
        this.reserveAmount = reserve;
        record(ClaimDomainEvent.ASSESSED, actor, data("liability", liability.name(),
                "recommendedAmount", recommendedAmount, "reserveAmount", reserve, "reassessment", reassessment));
        return assessment;
    }

    public void approve(BigDecimal amount, AuthorityPolicy authority, Actor actor, Instant now) {
        approve(amount, BigDecimal.ZERO, authority, actor, now);
    }

    /**
     * Approves a payment. {@code approvedOnOtherClaims} is what other claims on the same policy have already been
     * approved or paid; together with this amount it may not exceed the policy's sum insured. This closes the gap
     * where two claims open at the same time each saw the full remaining cover at FNOL.
     */
    public void approve(BigDecimal amount, BigDecimal approvedOnOtherClaims, AuthorityPolicy authority, Actor actor,
                        Instant now) {
        requireHandler(actor, "approve");
        if (status != ClaimStatus.ASSESSED) {
            throw ClaimRuleException.invalidTransition(status, ClaimStatus.APPROVED, "approve");
        }
        if (latestAssessment().map(a -> a.getLiability() == Liability.DENIED).orElse(true)) {
            throw ClaimRuleException.rule("The latest assessment denies liability. Reject the claim or re-assess it.");
        }
        if (amount == null || amount.signum() <= 0) {
            throw ClaimRuleException.rule("The approved amount must be greater than zero.");
        }
        requireWithinCover(amount, "approved amount");
        requireWithinPolicyLimit(amount, approvedOnOtherClaims);
        authority.checkCanApprove(actor, amount, currency);
        transition(ClaimStatus.APPROVED, actor, "Approved for " + currency + " " + amount.toPlainString(), "approve",
                now);
        this.approvedAmount = amount;
        this.reserveAmount = amount;
        record(ClaimDomainEvent.APPROVED, actor, data("amount", amount));
    }

    public void settle(String reference, Actor actor, Instant now) {
        requireHandler(actor, "settle");
        transition(ClaimStatus.SETTLED, actor, "Payment " + reference + " recorded", "settle", now);
        this.paidAmount = approvedAmount;
        this.paymentReference = reference;
        this.reserveAmount = BigDecimal.ZERO;
        this.closedAt = now;
        record(ClaimDomainEvent.SETTLED, actor, data("amount", paidAmount, "paymentReference", reference));
    }

    public void reject(RejectionReason reason, String explanation, Actor actor, Instant now) {
        requireHandler(actor, "reject");
        transition(ClaimStatus.REJECTED, actor, "Rejected: " + reason, "reject", now);
        this.rejectionReason = reason;
        this.decisionNote = explanation;
        this.reserveAmount = BigDecimal.ZERO;
        this.closedAt = now;
        record(ClaimDomainEvent.REJECTED, actor, data("reasonCode", reason.name(),
                "reasonDescription", reason.description(), "explanation", explanation));
    }

    public void withdraw(String reason, Actor actor, Instant now) {
        requireOwner(actor);
        transition(ClaimStatus.WITHDRAWN, actor, reason == null || reason.isBlank() ? "Withdrawn by claimant"
                : "Withdrawn by claimant: " + reason, "withdraw", now);
        infoRequests.forEach(InfoRequest::cancel);
        this.decisionNote = reason;
        this.reserveAmount = BigDecimal.ZERO;
        this.closedAt = now;
        record(ClaimDomainEvent.WITHDRAWN, actor, data("reason", reason));
    }

    // ------------------------------------------------------------------ Queries

    public void checkVersion(Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != version) {
            throw new ClaimRuleException(ClaimRuleException.Kind.STALE_VERSION,
                    "Claim " + claimNumber + " was changed by someone else. Reload it and try again.");
        }
    }

    public boolean isOwnedBy(UUID userId) {
        return claimantId.equals(userId);
    }

    public boolean isOverdue(Instant now) {
        return status.isOpen() && now.isAfter(slaDueAt);
    }

    public BigDecimal maxPayable() {
        return sumInsuredSnapshot.subtract(deductibleSnapshot).max(BigDecimal.ZERO);
    }

    public List<InfoRequest> openInfoRequests() {
        return infoRequests.stream().filter(InfoRequest::isOpen).toList();
    }

    public Optional<Assessment> latestAssessment() {
        return assessments.stream().max(Comparator.comparing(Assessment::getAssessedAt));
    }

    /** Returns and clears the events recorded since the last call. */
    public List<ClaimDomainEvent> pullEvents() {
        List<ClaimDomainEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    // ------------------------------------------------------------------ Internals

    private void transition(ClaimStatus target, Actor actor, String comment, String action, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw ClaimRuleException.invalidTransition(status, target, action);
        }
        history.add(new ClaimStatusHistory(this, status, target, actor, comment, now));
        this.status = target;
        touch(now);
    }

    private void touch(Instant now) {
        this.updatedAt = now;
    }

    private void setOfficer(UUID officerId, String officerName) {
        this.assignedOfficerId = officerId;
        this.assignedOfficerName = officerName;
    }

    private void requireStaff(Actor actor) {
        if (!actor.isStaff()) {
            throw ClaimRuleException.notPermitted("Only claims staff can do this.");
        }
    }

    private void requireHandler(Actor actor, String action) {
        requireStaff(actor);
        if (!actor.isManager() && !actor.id().equals(assignedOfficerId)) {
            throw ClaimRuleException.notPermitted("Only the assigned officer or a claims manager can " + action
                    + " this claim.");
        }
    }

    private void requireOwner(Actor actor) {
        if (!actor.isClaimant() || !isOwnedBy(actor.id())) {
            throw ClaimRuleException.notPermitted("Only the claimant who reported this claim can do this.");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw ClaimRuleException.rule("The " + name + " must be zero or more.");
        }
    }

    private void requireWithinCover(BigDecimal amount, String name) {
        if (amount.compareTo(maxPayable()) > 0) {
            throw ClaimRuleException.rule("The " + name + " of " + currency + " " + amount.toPlainString()
                    + " is more than the maximum payable under the policy (" + currency + " "
                    + maxPayable().toPlainString() + " after the deductible).");
        }
    }

    private void requireWithinPolicyLimit(BigDecimal amount, BigDecimal approvedOnOtherClaims) {
        BigDecimal others = approvedOnOtherClaims == null ? BigDecimal.ZERO : approvedOnOtherClaims;
        BigDecimal left = policySumInsured.subtract(others).max(BigDecimal.ZERO);
        if (amount.compareTo(left) > 0) {
            throw ClaimRuleException.rule("Other claims on policy " + policyNumber + " have already been approved or"
                    + " paid " + currency + " " + others.toPlainString() + ", so only " + currency + " "
                    + left.toPlainString() + " of the " + currency + " " + policySumInsured.toPlainString()
                    + " sum insured is left. The approved amount of " + currency + " " + amount.toPlainString()
                    + " is more than that.");
        }
    }

    private void record(String type, Actor actor, Map<String, Object> data) {
        pendingEvents.add(ClaimDomainEvent.of(type, actor, data));
    }

    private static Map<String, Object> data(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(map);
    }

    // ------------------------------------------------------------------ Getters

    public UUID getId() {
        return id;
    }

    public String getClaimNumber() {
        return claimNumber;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public UUID getClaimantId() {
        return claimantId;
    }

    public String getClaimantName() {
        return claimantName;
    }

    public ClaimType getClaimType() {
        return claimType;
    }

    public Market getMarket() {
        return market;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDate getIncidentDate() {
        return incidentDate;
    }

    public String getIncidentLocation() {
        return incidentLocation;
    }

    public String getDescription() {
        return description;
    }

    public boolean isInjuryReported() {
        return injuryReported;
    }

    public BigDecimal getEstimatedLoss() {
        return estimatedLoss;
    }

    public BigDecimal getSumInsuredSnapshot() {
        return sumInsuredSnapshot;
    }

    public BigDecimal getDeductibleSnapshot() {
        return deductibleSnapshot;
    }

    public String getInsuredItem() {
        return insuredItem;
    }

    public BigDecimal getPolicySumInsured() {
        return policySumInsured;
    }

    public String getPossibleDuplicateOf() {
        return possibleDuplicateOf;
    }

    public BigDecimal getReserveAmount() {
        return reserveAmount;
    }

    public BigDecimal getApprovedAmount() {
        return approvedAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public ClaimStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public UUID getAssignedOfficerId() {
        return assignedOfficerId;
    }

    public String getAssignedOfficerName() {
        return assignedOfficerName;
    }

    public RejectionReason getRejectionReason() {
        return rejectionReason;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getSlaDueAt() {
        return slaDueAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<ClaimStatusHistory> getHistory() {
        return Collections.unmodifiableList(history);
    }

    public List<InfoRequest> getInfoRequests() {
        return Collections.unmodifiableList(infoRequests);
    }

    public List<Assessment> getAssessments() {
        return Collections.unmodifiableList(assessments);
    }

    // ------------------------------------------------------------------ Value types

    /** What the claimant reported. */
    public record NewClaim(String policyNumber, ClaimType claimType, LocalDate incidentDate, String incidentLocation,
                           String description, boolean injuryReported, BigDecimal estimatedLoss) {
    }

    /** Coverage facts copied from policy-service at FNOL, so the claim never has to ask again. */
    public record CoverageSnapshot(Market market, String currency, BigDecimal remainingSumInsured,
                                   BigDecimal deductible, String insuredItem, BigDecimal policySumInsured) {

        /** For a policy with nothing paid yet: the full sum insured is still remaining. */
        public CoverageSnapshot(Market market, String currency, BigDecimal remainingSumInsured, BigDecimal deductible,
                                String insuredItem) {
            this(market, currency, remainingSumInsured, deductible, insuredItem, remainingSumInsured);
        }
    }
}
