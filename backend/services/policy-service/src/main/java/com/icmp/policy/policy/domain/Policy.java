package com.icmp.policy.policy.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @Column(name = "policy_number")
    private String policyNumber;

    @Column(name = "holder_user_id", nullable = false)
    private UUID holderUserId;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Product product;

    @Column(nullable = false)
    private String market;

    @Column(nullable = false)
    private String currency;

    @Column(name = "insured_item", nullable = false)
    private String insuredItem;

    @Column(name = "sum_insured", nullable = false)
    private BigDecimal sumInsured;

    @Column(nullable = false)
    private BigDecimal deductible;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status;

    @Column(name = "paid_to_date", nullable = false)
    private BigDecimal paidToDate;

    @Version
    private long version;

    protected Policy() {
        // JPA
    }

    public Policy(String policyNumber, UUID holderUserId, String holderName, Product product, String market,
                  String currency, String insuredItem, BigDecimal sumInsured, BigDecimal deductible,
                  LocalDate startDate, LocalDate endDate, PolicyStatus status) {
        this.policyNumber = policyNumber;
        this.holderUserId = holderUserId;
        this.holderName = holderName;
        this.product = product;
        this.market = market;
        this.currency = currency;
        this.insuredItem = insuredItem;
        this.sumInsured = sumInsured;
        this.deductible = deductible;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.paidToDate = BigDecimal.ZERO;
    }

    /**
     * Evaluates whether an incident is covered. Returns the list of reasons it is not; empty means covered.
     * Ownership is checked by the caller so that a foreign policy is reported as "not found".
     */
    public List<String> coverageProblems(LocalDate incidentDate, Product claimType) {
        List<String> problems = new ArrayList<>();
        if (status != PolicyStatus.ACTIVE) {
            problems.add("Policy " + policyNumber + " is not active (status " + status + ").");
        }
        if (incidentDate.isBefore(startDate) || incidentDate.isAfter(endDate)) {
            problems.add("Incident date " + incidentDate + " is outside the policy period " + startDate + " to "
                    + endDate + ".");
        }
        if (product != claimType) {
            problems.add("Policy " + policyNumber + " covers " + product + " claims, not " + claimType + ".");
        }
        if (remainingSumInsured().signum() <= 0) {
            problems.add("The sum insured on policy " + policyNumber + " has been fully used.");
        }
        return problems;
    }

    public BigDecimal remainingSumInsured() {
        return sumInsured.subtract(paidToDate).max(BigDecimal.ZERO);
    }

    public void recordPayment(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        this.paidToDate = this.paidToDate.add(amount);
    }

    public boolean isHeldBy(UUID userId) {
        return holderUserId.equals(userId);
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public UUID getHolderUserId() {
        return holderUserId;
    }

    public String getHolderName() {
        return holderName;
    }

    public Product getProduct() {
        return product;
    }

    public String getMarket() {
        return market;
    }

    public String getCurrency() {
        return currency;
    }

    public String getInsuredItem() {
        return insuredItem;
    }

    public BigDecimal getSumInsured() {
        return sumInsured;
    }

    public BigDecimal getDeductible() {
        return deductible;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public BigDecimal getPaidToDate() {
        return paidToDate;
    }
}
