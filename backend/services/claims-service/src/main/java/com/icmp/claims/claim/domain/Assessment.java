package com.icmp.claims.claim.domain;

import java.math.BigDecimal;
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

/** One assessment of the claim. A claim can be re-assessed; the latest one is current. */
@Entity
@Table(name = "assessment")
public class Assessment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Liability liability;

    @Column(name = "recommended_amount", nullable = false)
    private BigDecimal recommendedAmount;

    @Column(name = "reserve_amount", nullable = false)
    private BigDecimal reserveAmount;

    @Column(nullable = false, length = 4000)
    private String findings;

    @Column(name = "assessor_id", nullable = false)
    private UUID assessorId;

    @Column(name = "assessor_name")
    private String assessorName;

    @Column(name = "assessed_at", nullable = false)
    private Instant assessedAt;

    protected Assessment() {
        // JPA
    }

    Assessment(Claim claim, Liability liability, BigDecimal recommendedAmount, BigDecimal reserveAmount,
               String findings, Actor assessor, Instant now) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.liability = liability;
        this.recommendedAmount = recommendedAmount;
        this.reserveAmount = reserveAmount;
        this.findings = findings;
        this.assessorId = assessor.id();
        this.assessorName = assessor.name();
        this.assessedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public Liability getLiability() {
        return liability;
    }

    public BigDecimal getRecommendedAmount() {
        return recommendedAmount;
    }

    public BigDecimal getReserveAmount() {
        return reserveAmount;
    }

    public String getFindings() {
        return findings;
    }

    public UUID getAssessorId() {
        return assessorId;
    }

    public String getAssessorName() {
        return assessorName;
    }

    public Instant getAssessedAt() {
        return assessedAt;
    }
}
