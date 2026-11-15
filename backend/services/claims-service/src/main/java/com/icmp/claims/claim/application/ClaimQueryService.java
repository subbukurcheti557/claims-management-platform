package com.icmp.claims.claim.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.icmp.claims.claim.application.view.ClaimViews.ClaimDetail;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimSummary;
import com.icmp.claims.claim.application.view.ClaimViews.NoteView;
import com.icmp.claims.claim.application.view.ClaimViews.PageView;
import com.icmp.claims.claim.application.view.ClaimViews.TimelineEntry;
import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.claim.domain.Priority;
import com.icmp.claims.claim.infrastructure.persistence.ClaimNoteRepository;
import com.icmp.claims.claim.infrastructure.persistence.ClaimRepository;
import com.icmp.claims.claim.infrastructure.persistence.ClaimSpecifications;
import com.icmp.claims.common.error.ApiException;
import com.icmp.claims.common.security.CurrentUser;

@Service
@Transactional(readOnly = true)
public class ClaimQueryService {

    /** Which slice of claims a staff member is looking at. */
    public enum QueueScope {
        UNASSIGNED,
        MINE,
        TEAM,
        ALL
    }

    public record QueueFilter(QueueScope scope, ClaimStatus status, Market market, Priority priority,
                              ClaimType claimType, boolean overdueOnly, String search) {
    }

    private final ClaimRepository claims;
    private final ClaimNoteRepository notes;
    private final ClaimViewMapper mapper;
    private final Clock clock;

    public ClaimQueryService(ClaimRepository claims, ClaimNoteRepository notes, ClaimViewMapper mapper,
                             Clock clock) {
        this.claims = claims;
        this.notes = notes;
        this.mapper = mapper;
        this.clock = clock;
    }

    public PageView<ClaimSummary> myClaims(CurrentUser user, ClaimStatus status, Pageable pageable) {
        Specification<Claim> spec = Specification.where(ClaimSpecifications.ownedBy(user.id()))
                .and(ClaimSpecifications.hasStatus(status));
        return page(claims.findAll(spec, pageable), false);
    }

    public PageView<ClaimSummary> queue(CurrentUser user, QueueFilter filter, Pageable pageable) {
        Specification<Claim> scope = switch (filter.scope()) {
            case UNASSIGNED -> ClaimSpecifications.unassigned()
                    .and(ClaimSpecifications.hasStatus(ClaimStatus.SUBMITTED));
            case MINE -> ClaimSpecifications.assignedTo(user.id()).and(ClaimSpecifications.open());
            case TEAM -> ClaimSpecifications.open();
            case ALL -> ClaimSpecifications.all();
        };
        Specification<Claim> spec = Specification.where(scope)
                .and(ClaimSpecifications.hasStatus(filter.status()))
                .and(ClaimSpecifications.inMarket(filter.market()))
                .and(ClaimSpecifications.hasPriority(filter.priority()))
                .and(ClaimSpecifications.ofType(filter.claimType()))
                .and(filter.overdueOnly() ? ClaimSpecifications.overdue(Instant.now(clock)) : null)
                .and(ClaimSpecifications.matches(filter.search()));
        return page(claims.findAll(spec, pageable), true);
    }

    public ClaimDetail get(UUID claimId, CurrentUser user) {
        Claim claim = load(claimId, user);
        return mapper.detail(claim, user.isStaff(), user.isStaff() ? notes.countByClaimId(claimId) : 0);
    }

    public List<TimelineEntry> timeline(UUID claimId, CurrentUser user) {
        return mapper.timeline(load(claimId, user));
    }

    public List<NoteView> notes(UUID claimId, CurrentUser user) {
        if (!user.isStaff()) {
            throw ApiException.forbidden("Internal notes are only visible to claims staff.");
        }
        if (!claims.existsById(claimId)) {
            throw ApiException.notFound("Claim " + claimId + " was not found.");
        }
        return notes.findByClaimIdOrderByCreatedAtDesc(claimId).stream().map(mapper::note).toList();
    }

    /** Staff see any claim; claimants only their own, and a foreign claim is reported as not found. */
    private Claim load(UUID claimId, CurrentUser user) {
        return claims.findById(claimId)
                .filter(c -> user.isStaff() || c.isOwnedBy(user.id()))
                .orElseThrow(() -> ApiException.notFound("Claim " + claimId + " was not found."));
    }

    private PageView<ClaimSummary> page(Page<Claim> page, boolean staffView) {
        return new PageView<>(page.getContent().stream().map(c -> mapper.summary(c, staffView)).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
