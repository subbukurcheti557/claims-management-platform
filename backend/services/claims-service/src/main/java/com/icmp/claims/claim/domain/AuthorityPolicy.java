package com.icmp.claims.claim.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Approval authority: officers may approve up to a USD limit, managers have no limit. */
public class AuthorityPolicy {

    private final BigDecimal officerLimitUsd;
    private final CurrencyConverter converter;

    public AuthorityPolicy(BigDecimal officerLimitUsd, CurrencyConverter converter) {
        this.officerLimitUsd = officerLimitUsd;
        this.converter = converter;
    }

    public void checkCanApprove(Actor actor, BigDecimal amount, String currency) {
        if (actor.isManager()) {
            return;
        }
        BigDecimal usd = converter.toUsd(amount, currency);
        if (usd.compareTo(officerLimitUsd) > 0) {
            throw ClaimRuleException.notPermitted("Approving " + currency + " " + amount.toPlainString()
                    + " (about USD " + usd.setScale(0, RoundingMode.HALF_UP).toPlainString()
                    + ") exceeds your authority limit of USD " + officerLimitUsd.toPlainString()
                    + ". A claims manager must approve this claim.");
        }
    }

    public BigDecimal officerLimitUsd() {
        return officerLimitUsd;
    }
}
