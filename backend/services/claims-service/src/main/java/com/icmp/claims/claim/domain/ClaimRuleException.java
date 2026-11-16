package com.icmp.claims.claim.domain;

/**
 * A business rule on a claim was broken. The {@link Kind} tells the web layer which HTTP status to use,
 * without the domain depending on HTTP.
 */
public class ClaimRuleException extends RuntimeException {

    public enum Kind {
        /** The claim is not in a state that allows this action (409). */
        INVALID_TRANSITION,
        /** The input breaks a business rule (422). */
        RULE_VIOLATION,
        /** The actor may not perform this action on this claim (403). */
        NOT_PERMITTED,
        /** The caller's view of the claim is out of date (409). */
        STALE_VERSION,
        /** The same incident on the same policy is already being handled by another claim (409). */
        DUPLICATE_CLAIM
    }

    private final Kind kind;

    public ClaimRuleException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public static ClaimRuleException invalidTransition(ClaimStatus from, ClaimStatus to, String action) {
        return new ClaimRuleException(Kind.INVALID_TRANSITION,
                "Cannot " + action + " a claim that is " + from + " (would move " + from + " to " + to + ").");
    }

    public static ClaimRuleException rule(String message) {
        return new ClaimRuleException(Kind.RULE_VIOLATION, message);
    }

    public static ClaimRuleException notPermitted(String message) {
        return new ClaimRuleException(Kind.NOT_PERMITTED, message);
    }
}
