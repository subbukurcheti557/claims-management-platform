package com.icmp.claims.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import com.icmp.claims.claim.domain.CurrencyConverter;

/** Converts with a fixed rate table from configuration. */
public class StaticFxRateConverter implements CurrencyConverter {

    private final Map<String, BigDecimal> ratesToUsd;

    public StaticFxRateConverter(Map<String, BigDecimal> ratesToUsd) {
        this.ratesToUsd = Map.copyOf(ratesToUsd);
    }

    @Override
    public BigDecimal toUsd(BigDecimal amount, String currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal rate = ratesToUsd.get(currency);
        if (rate == null) {
            throw new IllegalStateException("No FX rate configured for " + currency);
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
