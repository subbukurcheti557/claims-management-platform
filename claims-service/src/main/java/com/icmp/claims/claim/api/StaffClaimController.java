package com.icmp.claims.claim.api;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.icmp.claims.claim.api.ClaimRequests.ApproveRequest;
import com.icmp.claims.claim.api.ClaimRequests.AssessmentRequest;
import com.icmp.claims.claim.api.ClaimRequests.AssignRequest;
import com.icmp.claims.claim.api.ClaimRequests.InfoRequestRequest;
import com.icmp.claims.claim.api.ClaimRequests.NoteRequest;
import com.icmp.claims.claim.api.ClaimRequests.RejectRequest;
import com.icmp.claims.claim.api.ClaimRequests.SettleRequest;
import com.icmp.claims.claim.application.ClaimCommandService;
import com.icmp.claims.claim.application.ClaimCommands;
import com.icmp.claims.claim.application.ClaimQueryService;
import com.icmp.claims.claim.application.ClaimQueryService.QueueFilter;
import com.icmp.claims.claim.application.ClaimQueryService.QueueScope;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimDetail;
import com.icmp.claims.claim.application.view.ClaimViews.ClaimSummary;
import com.icmp.claims.claim.application.view.ClaimViews.NoteView;
import com.icmp.claims.claim.application.view.ClaimViews.PageView;
import com.icmp.claims.claim.domain.ClaimStatus;
import com.icmp.claims.claim.domain.ClaimType;
import com.icmp.claims.claim.domain.Market;
import com.icmp.claims.claim.domain.Priority;
import com.icmp.claims.common.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Staff endpoints. Commands are intent-based (assign, assess, approve...) rather than a generic status PATCH, so
 * each one is authorised and validated against the state machine on its own terms.
 */
@RestController
@RequestMapping("/api/claims")
@PreAuthorize("hasAnyRole('CLAIMS_OFFICER','CLAIMS_MANAGER')")
@Tag(name = "Claims - staff")
public class StaffClaimController {

    private final ClaimCommandService commands;
    private final ClaimQueryService queries;

    public StaffClaimController(ClaimCommandService commands, ClaimQueryService queries) {
        this.commands = commands;
        this.queries = queries;
    }

    @GetMapping("/queue")
    @Operation(summary = "Work queue: UNASSIGNED (pick-up), MINE, TEAM (all open) or ALL, with filters")
    public PageView<ClaimSummary> queue(@RequestParam(defaultValue = "UNASSIGNED") QueueScope scope,
                                        @RequestParam(required = false) ClaimStatus status,
                                        @RequestParam(required = false) Market market,
                                        @RequestParam(required = false) Priority priority,
                                        @RequestParam(required = false) ClaimType claimType,
                                        @RequestParam(defaultValue = "false") boolean overdueOnly,
                                        @RequestParam(required = false) String q,
                                        @PageableDefault(size = 20, sort = "slaDueAt",
                                                direction = Sort.Direction.ASC) Pageable pageable) {
        return queries.queue(CurrentUser.get(),
                new QueueFilter(scope, status, market, priority, claimType, overdueOnly, q), pageable);
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Pick up a claim (officer, no officerId) or assign/reassign it (manager)")
    public ClaimDetail assign(@PathVariable UUID id, @Valid @RequestBody(required = false) AssignRequest r) {
        AssignRequest req = r == null ? new AssignRequest(null, null) : r;
        return commands.assign(new ClaimCommands.Assign(id, req.expectedVersion(), req.officerId()),
                CurrentUser.get());
    }

    @PostMapping("/{id}/info-requests")
    @Operation(summary = "Ask the claimant for more information")
    public ClaimDetail requestInformation(@PathVariable UUID id, @Valid @RequestBody InfoRequestRequest r) {
        return commands.requestInformation(new ClaimCommands.RequestInfo(id, r.expectedVersion(),
                r.question().trim()), CurrentUser.get());
    }

    @PostMapping("/{id}/assessment")
    @Operation(summary = "Record an assessment: liability, recommended amount, reserve, findings")
    public ClaimDetail assess(@PathVariable UUID id, @Valid @RequestBody AssessmentRequest r) {
        return commands.assess(new ClaimCommands.Assess(id, r.expectedVersion(), r.liability(),
                r.recommendedAmount(), r.reserveAmount(), r.findings().trim()), CurrentUser.get());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve the claim for an amount (officer authority limit applies)")
    public ClaimDetail approve(@PathVariable UUID id, @Valid @RequestBody ApproveRequest r) {
        return commands.approve(new ClaimCommands.Approve(id, r.expectedVersion(), r.amount()), CurrentUser.get());
    }

    @PostMapping("/{id}/settle")
    @Operation(summary = "Record the settlement payment for an approved claim")
    public ClaimDetail settle(@PathVariable UUID id, @Valid @RequestBody SettleRequest r) {
        return commands.settle(new ClaimCommands.Settle(id, r.expectedVersion(), r.paymentReference().trim()),
                CurrentUser.get());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject the claim with a reason code and an explanation for the claimant")
    public ClaimDetail reject(@PathVariable UUID id, @Valid @RequestBody RejectRequest r) {
        return commands.reject(new ClaimCommands.Reject(id, r.expectedVersion(), r.reasonCode(),
                r.explanation().trim()), CurrentUser.get());
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "Internal notes, newest first")
    public List<NoteView> notes(@PathVariable UUID id) {
        return queries.notes(id, CurrentUser.get());
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an internal note")
    public NoteView addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest r) {
        return commands.addNote(new ClaimCommands.AddNote(id, r.body().trim()), CurrentUser.get());
    }
}
