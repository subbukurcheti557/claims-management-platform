package com.icmp.claims.config;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Business configuration for claims-service.
 *
 * @param fxToUsd           static rates used to convert local currency to USD (assumption: no live FX feed)
 * @param officerLimitUsd   maximum a claims officer may approve
 * @param highValueUsd      estimated loss above which a claim is HIGH priority
 * @param mediumValueUsd    estimated loss above which a claim is at least MEDIUM priority
 */
@ConfigurationProperties(prefix = "app.claims")
public record ClaimsProperties(Map<String, BigDecimal> fxToUsd, BigDecimal officerLimitUsd, BigDecimal highValueUsd,
                               BigDecimal mediumValueUsd) {
}
