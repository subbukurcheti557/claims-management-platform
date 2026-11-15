package com.icmp.claims.claim.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Append-only audit trail: one row per status change (and per reassignment). Never updated. */
@Entity
@Table(name = "claim_status_history")
public class ClaimStatusHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private ClaimStatus toStatus;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_name")
    private String actorName;

    @Column(name = "actor_role", nullable = false)
    private String actorRole;

    @Column(name = "comment")
    private String comment;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ClaimStatusHistory() {
        // JPA
    }

    ClaimStatusHistory(Claim claim, ClaimStatus fromStatus, ClaimStatus toStatus, Actor actor, String comment,
                       Instant occurredAt) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.actorId = actor.id();
        this.actorName = actor.name();
        this.actorRole = actor.role();
        this.comment = comment;
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public ClaimStatus getFromStatus() {
        return fromStatus;
    }

    public ClaimStatus getToStatus() {
        return toStatus;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public String getComment() {
        return comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
