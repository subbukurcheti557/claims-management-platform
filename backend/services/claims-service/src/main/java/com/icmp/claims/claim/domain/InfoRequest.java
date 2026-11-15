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

/** A question from the claims officer that the claimant must answer. */
@Entity
@Table(name = "info_request")
public class InfoRequest {

    public enum Status {
        OPEN,
        ANSWERED,
        CANCELLED
    }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(length = 4000)
    private String response;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_by_name")
    private String requestedByName;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    protected InfoRequest() {
        // JPA
    }

    InfoRequest(Claim claim, String question, Actor requester, Instant now) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.question = question;
        this.status = Status.OPEN;
        this.requestedBy = requester.id();
        this.requestedByName = requester.name();
        this.requestedAt = now;
    }

    void answer(String answer, Instant now) {
        if (status != Status.OPEN) {
            throw ClaimRuleException.rule("This information request has already been " + status.name().toLowerCase()
                    + ".");
        }
        this.response = answer;
        this.status = Status.ANSWERED;
        this.respondedAt = now;
    }

    void cancel() {
        if (status == Status.OPEN) {
            status = Status.CANCELLED;
        }
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public UUID getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public String getResponse() {
        return response;
    }

    public Status getStatus() {
        return status;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public String getRequestedByName() {
        return requestedByName;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }
}
