package com.icmp.claims.claim.domain;

import java.math.BigDecimal;

/** Converts local-currency amounts to USD for authority limits and reporting. */
public interface CurrencyConverter {

    BigDecimal toUsd(BigDecimal amount, String currency);
}
