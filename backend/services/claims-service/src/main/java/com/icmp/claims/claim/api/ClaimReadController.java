package com.icmp.claims.claim.api;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.claims.claim.application.ClaimQueryService;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimDetail;
import com.icmp.claims.claim.application.view.ClaimViews.TimelineEntry;
import com.icmp.claims.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Endpoints shared by both user types. The response depends on the caller: claimants get the redacted view of
 * their own claims only, staff get the full view of any claim.
 */
@RestController
@RequestMapping("/api/claims")
@Tag(name = "Claims - shared")
public class ClaimReadController {

    private final ClaimQueryService queries;

    public ClaimReadController(ClaimQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{id}")
    @Operation(summary = "One claim (claimant: own claims only, redacted; staff: full view)")
    public ClaimDetail get(@PathVariable UUID id) {
        return queries.get(id, CurrentUser.get());
    }

    @GetMapping("/{id}/timeline")
    @Operation(summary = "Status history of a claim")
    public List<TimelineEntry> timeline(@PathVariable UUID id) {
        return queries.timeline(id, CurrentUser.get());
    }
}
