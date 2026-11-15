package com.icmp.claims.claim.infrastructure.persistence;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.claim.domain.Priority;

/** Composable filters for the claimant list and the staff work queue (Specification pattern). */
public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    public static Specification<Claim> all() {
        return (root, query, cb) -> cb.conjunction();
    }

    public static Specification<Claim> ownedBy(UUID claimantId) {
        return (root, query, cb) -> cb.equal(root.get("claimantId"), claimantId);
    }

    public static Specification<Claim> assignedTo(UUID officerId) {
        return (root, query, cb) -> cb.equal(root.get("assignedOfficerId"), officerId);
    }

    public static Specification<Claim> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignedOfficerId"));
    }

    public static Specification<Claim> open() {
        return (root, query, cb) -> root.get("status").in(ClaimStatus.OPEN);
    }

    public static Specification<Claim> hasStatus(ClaimStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Claim> inMarket(Market market) {
        return market == null ? null : (root, query, cb) -> cb.equal(root.get("market"), market);
    }

    public static Specification<Claim> hasPriority(Priority priority) {
        return priority == null ? null : (root, query, cb) -> cb.equal(root.get("priority"), priority);
    }

    public static Specification<Claim> ofType(ClaimType type) {
        return type == null ? null : (root, query, cb) -> cb.equal(root.get("claimType"), type);
    }

    public static Specification<Claim> overdue(Instant now) {
        return (root, query, cb) -> cb.and(root.get("status").in(ClaimStatus.OPEN),
                cb.lessThan(root.get("slaDueAt"), now));
    }

    /** Free-text search on claim number, policy number and claimant name. */
    public static Specification<Claim> matches(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String like = "%" + text.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("claimNumber")), like),
                cb.like(cb.lower(root.get("policyNumber")), like),
                cb.like(cb.lower(root.get("claimantName")), like));
    }
}
