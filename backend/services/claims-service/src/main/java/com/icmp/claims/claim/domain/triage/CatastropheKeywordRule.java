package com.icmp.claims.claim.domain.triage;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Priority;

/** Property claims that mention a catastrophe peril are at least MEDIUM: they tend to arrive in clusters. */
public class CatastropheKeywordRule implements PriorityRule {

    private static final List<String> PERILS = List.of("flood", "fire", "bushfire", "typhoon", "cyclone", "storm",
            "earthquake", "landslide");

    @Override
    public Optional<Priority> evaluate(TriageInput input) {
        if (input.claimType() != ClaimType.PROPERTY || input.description() == null) {
            return Optional.empty();
        }
        String text = input.description().toLowerCase(Locale.ROOT);
        return PERILS.stream().anyMatch(text::contains) ? Optional.of(Priority.MEDIUM) : Optional.empty();
    }
}
