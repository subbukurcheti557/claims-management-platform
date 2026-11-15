package com.icmp.claims.claim.application.port;

import java.util.List;

import com.icmp.claims.claim.domain.Claim;
import com.icmp.claims.claim.domain.ClaimDomainEvent;

/** Stores events for later publication. Must be called inside the transaction that changes the claim. */
public interface EventOutboxPort {

    void append(Claim claim, List<ClaimDomainEvent> events);
}
