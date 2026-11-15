package com.icmp.claims.claim.api;

import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.claims.claim.api.ClaimRequests.InfoResponseRequest;
import com.icmp.claims.claim.api.ClaimRequests.SubmitClaimRequest;
import com.icmp.claims.claim.api.ClaimRequests.WithdrawRequest;
import com.icmp.claims.claim.application.ClaimCommandService;
import com.icmp.claims.claim.application.ClaimCommands;
import com.icmp.claims.claim.application.ClaimQueryService;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimDetail;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimSummary;
import com.icmp.claims.claim.application.view.ClaimViews.PageView;
import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Claimant-facing endpoints: report, track, respond, withdraw. */
@RestController
@RequestMapping("/api/claims")
@PreAuthorize("hasRole('CLAIMANT')")
@Tag(name = "Claims - claimant")
public class ClaimantClaimController {

    private final ClaimCommandService commands;
    private final ClaimQueryService queries;

    public ClaimantClaimController(ClaimCommandService commands, ClaimQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @PostMapping
    @Operation(summary = "Report an incident (FNOL). Synchronously checks policy coverage.")
    public ResponseEntity<ClaimDetail> submit(@Valid @RequestBody SubmitClaimRequest r) {
        ClaimDetail created = commands.submit(new ClaimCommands.SubmitClaim(r.policyNumber().trim(), r.claimType(),
                r.incidentDate(), r.incidentLocation().trim(), r.description().trim(), r.injuryReported(),
                r.estimatedLoss()), CurrentUser.get());
        return ResponseEntity.created(URI.create("/api/claims/" + created.id())).body(created);
    }

    @GetMapping("/mine")
    @Operation(summary = "The claimant's own claims, newest first")
    public PageView<ClaimSummary> mine(@RequestParam(required = false) ClaimStatus status,
                                       @PageableDefault(size = 20, sort = "submittedAt",
                                               direction = Sort.Direction.DESC) Pageable pageable) {
        return queries.myClaims(CurrentUser.get(), status, pageable);
    }

    @PostMapping("/{id}/info-requests/{requestId}/response")
    @Operation(summary = "Answer an officer's request for information")
    public ClaimDetail respond(@PathVariable UUID id, @PathVariable UUID requestId,
                               @Valid @RequestBody InfoResponseRequest r) {
        return commands.respondToInformationRequest(
                new ClaimCommands.RespondToInfo(id, requestId, r.response().trim()), CurrentUser.get());
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw a claim that has not been assessed")
    public ClaimDetail withdraw(@PathVariable UUID id, @Valid @RequestBody(required = false) WithdrawRequest r) {
        return commands.withdraw(new ClaimCommands.Withdraw(id, r == null ? null : r.reason()), CurrentUser.get());
    }
}
