package com.icmp.claims.claim.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.icmp.claims.claim.application.ClaimCommands.AddNote;
import com.icmp.claims.claim.application.ClaimCommands.Approve;
import com.icmp.claims.claim.application.ClaimCommands.Assess;
import com.icmp.claims.claim.application.ClaimCommands.Assign;
import com.icmp.claims.claim.application.ClaimCommands.Reject;
import com.icmp.claims.claim.application.ClaimCommands.RequestInfo;
import com.icmp.claims.claim.application.ClaimCommands.RespondToInfo;
import com.icmp.claims.claim.application.ClaimCommands.Settle;
import com.icmp.claims.claim.application.ClaimCommands.SubmitClaim;
import com.icmp.claims.claim.application.ClaimCommands.Withdraw;
import com.icmp.claims.claim.application.port.EventOutboxPort;
import com.icmp.claims.claim.application.port.PolicyCoveragePort;
import com.icmp.claims.claim.application.port.PolicyCoveragePort.Coverage;
import com.icmp.claims.claim.application.port.StaffDirectoryPort;
import com.icmp.claims.claim.application.port.StaffDirectoryPort.StaffMember;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimDetail;
import com.icmp.claims.claim.application.view.ClaimViews.NoteView;
import com.icmp.claims.claim.domain.Actor;
import com.icmp.claims.claim.domain.AuthorityPolicy;
import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimDomainEvent;
import com.icmp.claims.claim.domain.ClaimNote;
import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.claim.domain.DuplicateClaimPolicy;
import com.icmp.claims.claim.domain.CurrencyConverter;
import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.claim.domain.Priority;
import com.icmp.claims.claim.domain.triage.TriageInput;
import com.icmp.claims.claim.domain.triage.TriagePolicy;
import com.icmp.claims.claim.infrastructure.persistence.ClaimNoteRepository;
import com.icmp.claims.claim.infrastructure.persistence.ClaimRepository;
import com.icmp.claims.common.error.ApiException;
import com.icmp.claims.common.security.CurrentUser;
import com.icmp.claims.document.domain.DocumentCategory;
import com.icmp.claims.document.domain.EvidencePolicy;
import com.icmp.claims.document.infrastructure.ClaimDocumentRepository;

/**
 * Use cases that change claims. Each command loads the aggregate, calls one intent method, writes the recorded
 * events to the outbox and commits, all in one transaction. Remote calls happen before the transaction starts.
 */
@Service
public class ClaimCommandService {

    private static final Logger log = LoggerFactory.getLogger(ClaimCommandService.class);

    /** Claims whose approved amount counts against the policy's sum insured. */
    private static final Set<ClaimStatus> APPROVED_OR_PAID = EnumSet.of(ClaimStatus.APPROVED, ClaimStatus.SETTLED);

    private final ClaimRepository claims;
    private final ClaimNoteRepository notes;
    private final ClaimDocumentRepository documents;
    private final EventOutboxPort outbox;
    private final PolicyCoveragePort policyCoverage;
    private final StaffDirectoryPort staffDirectory;
    private final TriagePolicy triagePolicy;
    private final AuthorityPolicy authorityPolicy;
    private final EvidencePolicy evidencePolicy;
    private final CurrencyConverter converter;
    private final ClaimViewMapper mapper;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ClaimCommandService(ClaimRepository claims, ClaimNoteRepository notes,
                               ClaimDocumentRepository documents, EventOutboxPort outbox,
                               PolicyCoveragePort policyCoverage, StaffDirectoryPort staffDirectory,
                               TriagePolicy triagePolicy, AuthorityPolicy authorityPolicy,
                               EvidencePolicy evidencePolicy, CurrencyConverter converter, ClaimViewMapper mapper,
                               TransactionTemplate tx, Clock clock) {
        this.claims = claims;
        this.notes = notes;
        this.documents = documents;
        this.outbox = outbox;
        this.policyCoverage = policyCoverage;
        this.staffDirectory = staffDirectory;
        this.triagePolicy = triagePolicy;
        this.authorityPolicy = authorityPolicy;
        this.evidencePolicy = evidencePolicy;
        this.converter = converter;
        this.mapper = mapper;
        this.tx = tx;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Claimant

    public ClaimDetail submit(SubmitClaim cmd, CurrentUser user) {
        Actor claimant = actor(user);
        if (!claimant.isClaimant()) {
            throw ApiException.forbidden("Only claimants can report a claim.");
        }
        // 1. synchronous coverage check, outside the DB transaction
        Coverage coverage = policyCoverage.checkCoverage(cmd.policyNumber(), user.id(), cmd.incidentDate(),
                        cmd.claimType(), user.token())
                .orElseThrow(() -> ApiException.notFound("Policy " + cmd.policyNumber() + " was not found."));
        if (!coverage.covered()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Not covered",
                    String.join(" ", coverage.reasons()));
        }
        // 2. triage
        Priority priority = triagePolicy.prioritise(new TriageInput(cmd.claimType(),
                converter.toUsd(cmd.estimatedLoss(), coverage.currency()), cmd.injuryReported(),
                cmd.description()));
        Market market = Market.valueOf(coverage.market());
        Claim.NewClaim report = new Claim.NewClaim(cmd.policyNumber(), cmd.claimType(), cmd.incidentDate(),
                cmd.incidentLocation(), cmd.description(), cmd.injuryReported(), cmd.estimatedLoss());
        Claim.CoverageSnapshot snapshot = new Claim.CoverageSnapshot(market, coverage.currency(),
                coverage.remainingSumInsured(), coverage.deductible(), coverage.insuredItem(), coverage.sumInsured());
        // 3. one transaction: duplicate check + claim + history + outbox, serialised per policy
        return tx.execute(status -> {
            claims.lockPolicy(cmd.policyNumber());
            String possibleDuplicateOf = DuplicateClaimPolicy.check(cmd.policyNumber(), cmd.incidentDate(),
                    claims.findByPolicyNumberAndIncidentDate(cmd.policyNumber(), cmd.incidentDate())).orElse(null);
            Instant now = Instant.now(clock);
            String number = "CLM-%s-%d-%06d".formatted(market.name(), now.atZone(ZoneOffset.UTC).getYear(),
                    claims.nextClaimSequence());
            Claim claim = Claim.submit(number, report, snapshot, priority, claimant, now, possibleDuplicateOf);
            List<ClaimDomainEvent> events = claim.pullEvents();
            claims.saveAndFlush(claim);
            outbox.append(claim, events);
            if (possibleDuplicateOf != null) {
                log.warn("Claim {} on policy {} flagged as a possible duplicate of paid claim {}", number,
                        cmd.policyNumber(), possibleDuplicateOf);
            }
            log.info("Claim {} submitted on policy {} with priority {}", number, cmd.policyNumber(),
                    claim.getPriority());
            return mapper.detail(claim, false, 0);
        });
    }

    public ClaimDetail respondToInformationRequest(RespondToInfo cmd, CurrentUser user) {
        return changeAsClaimant(cmd.claimId(), user, claim ->
                claim.respondToInformationRequest(cmd.requestId(), cmd.response(), actor(user), now()));
    }

    public ClaimDetail withdraw(Withdraw cmd, CurrentUser user) {
        return changeAsClaimant(cmd.claimId(), user, claim -> claim.withdraw(cmd.reason(), actor(user), now()));
    }

    // ------------------------------------------------------------------ Staff

    public ClaimDetail assign(Assign cmd, CurrentUser user) {
        UUID officerId = cmd.officerId() == null ? user.id() : cmd.officerId();
        String officerName;
        if (officerId.equals(user.id())) {
            officerName = user.name();
        } else {
            if (!user.isManager()) {
                throw ApiException.forbidden("Claims officers can only pick up claims for themselves.");
            }
            StaffMember member = staffDirectory.findStaff(officerId, user.token())
                    .filter(StaffMember::isClaimsStaff)
                    .orElseThrow(() -> ApiException.unprocessable("User " + officerId
                            + " is not an active claims officer or manager."));
            officerName = member.fullName();
        }
        return changeAsStaff(cmd.claimId(), cmd.expectedVersion(), user,
                claim -> claim.assign(officerId, officerName, actor(user), now()));
    }

    public ClaimDetail requestInformation(RequestInfo cmd, CurrentUser user) {
        return changeAsStaff(cmd.claimId(), cmd.expectedVersion(), user,
                claim -> claim.requestInformation(cmd.question(), actor(user), now()));
    }

    public ClaimDetail assess(Assess cmd, CurrentUser user) {
        return changeAsStaff(cmd.claimId(), cmd.expectedVersion(), user,
                claim -> claim.assess(cmd.liability(), cmd.recommendedAmount(), cmd.reserveAmount(), cmd.findings(),
                        actor(user), now()));
    }

    public ClaimDetail approve(Approve cmd, CurrentUser user) {
        return changeAsStaff(cmd.claimId(), cmd.expectedVersion(), user, claim -> {
            claims.lockPolicy(claim.getPolicyNumber());
            BigDecimal approvedOnOthers = claims.sumApprovedOnOtherClaims(claim.getPolicyNumber(), claim.getId(),
                    APPROVED_OR_PAID);
            claim.approve(cmd.amount(), approvedOnOthers, authorityPolicy, actor(user), now());
            // after the status, authority and cover checks; throwing here rolls the approval back
            evidencePolicy.checkReadyForApproval(claim,
                    documents.countByClaimIdAndCategory(claim.getId(), DocumentCategory.DAMAGE_PHOTO));
        });
    }

    public ClaimDetail settle(Settle cmd, CurrentUser user) {
        return changeAsStaff(cmd.claimId(), cmd.expectedVersion(), user,
                claim -> claim.settle(cmd.paymentReference(), actor(user), now()));
    }

    public ClaimDetail reject(Reject cmd, CurrentUser user) {
        return changeAsStaff(cmd.claimId(), cmd.expectedVersion(), user,
                claim -> claim.reject(cmd.reason(), cmd.explanation(), actor(user), now()));
    }

    /** Internal notes do not change the claim, so they are not versioned and not published. */
    public NoteView addNote(AddNote cmd, CurrentUser user) {
        requireStaff(user);
        return tx.execute(status -> {
            if (!claims.existsById(cmd.claimId())) {
                throw notFound(cmd.claimId());
            }
            ClaimNote note = notes.save(new ClaimNote(cmd.claimId(), actor(user), cmd.body()));
            return mapper.note(note);
        });
    }

    // ------------------------------------------------------------------ Internals

    private ClaimDetail changeAsStaff(UUID claimId, Long expectedVersion, CurrentUser user, Consumer<Claim> change) {
        requireStaff(user);
        return tx.execute(status -> {
            Claim claim = claims.findById(claimId).orElseThrow(() -> notFound(claimId));
            claim.checkVersion(expectedVersion);
            change.accept(claim);
            return persist(claim, true);
        });
    }

    private ClaimDetail changeAsClaimant(UUID claimId, CurrentUser user, Consumer<Claim> change) {
        if (!user.isClaimant()) {
            throw ApiException.forbidden("Only the claimant can do this.");
        }
        return tx.execute(status -> {
            Claim claim = claims.findById(claimId).filter(c -> c.isOwnedBy(user.id()))
                    .orElseThrow(() -> notFound(claimId));
            change.accept(claim);
            return persist(claim, false);
        });
    }

    private ClaimDetail persist(Claim claim, boolean staffView) {
        List<ClaimDomainEvent> events = claim.pullEvents();
        claims.saveAndFlush(claim);
        outbox.append(claim, events);
        return mapper.detail(claim, staffView, staffView ? notes.countByClaimId(claim.getId()) : 0);
    }

    private static void requireStaff(CurrentUser user) {
        if (!user.isStaff()) {
            throw ApiException.forbidden("Only claims staff can do this.");
        }
    }

    private static Actor actor(CurrentUser user) {
        return new Actor(user.id(), user.name(), user.role());
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private static ApiException notFound(UUID claimId) {
        return ApiException.notFound("Claim " + claimId + " was not found.");
    }
}
