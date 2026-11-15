package com.icmp.policy.policy.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.icmp.policy.policy.domain.Policy;

public record PolicyResponse(String policyNumber, String holderName, String product, String market, String currency,
                             String insuredItem, BigDecimal sumInsured, BigDecimal deductible, LocalDate startDate,
                             LocalDate endDate, String status, BigDecimal paidToDate, BigDecimal remainingSumInsured) {

    public static PolicyResponse from(Policy p) {
        return new PolicyResponse(p.getPolicyNumber(), p.getHolderName(), p.getProduct().name(), p.getMarket(),
                p.getCurrency(), p.getInsuredItem(), p.getSumInsured(), p.getDeductible(), p.getStartDate(),
                p.getEndDate(), p.getStatus().name(), p.getPaidToDate(), p.remainingSumInsured());
    }
}
