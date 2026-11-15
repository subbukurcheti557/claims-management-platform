package com.icmp.claims.config;

import java.time.Clock;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.icmp.claims.claim.domain.AuthorityPolicy;
import com.icmp.claims.claim.domain.CurrencyConverter;
import com.icmp.claims.claim.domain.triage.CatastropheKeywordRule;
import com.icmp.claims.claim.domain.triage.HighValueRule;
import com.icmp.claims.claim.domain.triage.InjuryRule;
import com.icmp.claims.claim.domain.triage.PriorityRule;
import com.icmp.claims.claim.domain.triage.TriagePolicy;

/** Wires the framework-free domain services. Adding a triage rule means adding one bean here. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ClaimsProperties.class)
public class DomainConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    CurrencyConverter currencyConverter(ClaimsProperties props) {
        return new StaticFxRateConverter(props.fxToUsd());
    }

    @Bean
    AuthorityPolicy authorityPolicy(ClaimsProperties props, CurrencyConverter converter) {
        return new AuthorityPolicy(props.officerLimitUsd(), converter);
    }

    @Bean
    PriorityRule highValueRule(ClaimsProperties props) {
        return new HighValueRule(props.highValueUsd(), props.mediumValueUsd());
    }

    @Bean
    PriorityRule injuryRule() {
        return new InjuryRule();
    }

    @Bean
    PriorityRule catastropheKeywordRule() {
        return new CatastropheKeywordRule();
    }

    @Bean
    TriagePolicy triagePolicy(List<PriorityRule> rules) {
        return new TriagePolicy(rules);
    }
}
