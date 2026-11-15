package com.icmp.policy.policy.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class PolicyTest {

    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 12, 31);

    private Policy policy(PolicyStatus status) {
        return new Policy("SG-MOT-1", UUID.randomUUID(), "A", Product.MOTOR, "SG", "SGD", "Car",
                new BigDecimal("10000"), new BigDecimal("500"), START, END, status);
    }

    @Test
    void activePolicyCoversMatchingIncidentInsidePeriod() {
        assertThat(policy(PolicyStatus.ACTIVE).coverageProblems(LocalDate.of(2026, 6, 1), Product.MOTOR)).isEmpty();
    }

    @Test
    void periodBoundariesAreInclusive() {
        Policy p = policy(PolicyStatus.ACTIVE);
        assertThat(p.coverageProblems(START, Product.MOTOR)).isEmpty();
        assertThat(p.coverageProblems(END, Product.MOTOR)).isEmpty();
    }

    @Test
    void reportsEveryProblemAtOnce() {
        assertThat(policy(PolicyStatus.LAPSED).coverageProblems(LocalDate.of(2027, 2, 1), Product.PROPERTY))
                .hasSize(3)
                .anyMatch(s -> s.contains("not active"))
                .anyMatch(s -> s.contains("outside the policy period"))
                .anyMatch(s -> s.contains("covers MOTOR claims, not PROPERTY"));
    }

    @Test
    void exhaustedSumInsuredIsNotCovered() {
        Policy p = policy(PolicyStatus.ACTIVE);
        p.recordPayment(new BigDecimal("10000"));
        assertThat(p.remainingSumInsured()).isEqualByComparingTo("0");
        assertThat(p.coverageProblems(LocalDate.of(2026, 6, 1), Product.MOTOR))
                .singleElement().asString().contains("fully used");
    }

    @Test
    void paymentsAccumulateAndMustBePositive() {
        Policy p = policy(PolicyStatus.ACTIVE);
        p.recordPayment(new BigDecimal("1200.50"));
        p.recordPayment(new BigDecimal("300"));
        assertThat(p.getPaidToDate()).isEqualByComparingTo("1500.50");
        assertThat(p.remainingSumInsured()).isEqualByComparingTo("8499.50");
        assertThatThrownBy(() -> p.recordPayment(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
    }
}
