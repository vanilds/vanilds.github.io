package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.web.ETags;
import io.github.vanilds.creditdesk.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Two layers of authorization, on purpose:
 * <ul>
 *   <li>{@code @PreAuthorize} here: <i>which role</i> may call the endpoint at all (cheap, before any query).</li>
 *   <li>The entity ({@link Proposal#apply}): <i>which person</i> may act on <i>this</i> proposal (owner,
 *   four-eyes). That needs the data, so it cannot live in an annotation.</li>
 * </ul>
 * Workflow actions are separate POST endpoints instead of {@code PATCH status=APPROVED}: each one is a business
 * command with its own rules and its own audit action, and the contract shows exactly which commands exist.
 */
@RestController
@RequestMapping("/api/proposals")
@Tag(name = "Proposals", description = "New credit and renegotiation proposals and their approval workflow")
public class ProposalController {

    private static final String IF_MATCH = "ETag from the last read, e.g. \"3\". Required: missing → 428, stale → 412.";

    private final ProposalService service;

    public ProposalController(ProposalService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "searchProposals", summary = "Search proposals",
            description = "Newest first. The period is at most 90 days and defaults to the last 30 days.")
    public PageResponse<ProposalResponse> search(
            @RequestParam(required = false) ProposalStatus status,
            @Parameter(description = "First day, inclusive (São Paulo date), e.g. 2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive (São Paulo date). Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID customerId,
            @Parameter(description = "Only proposals owned by this analyst (Keycloak id). Use mine=true for your own.")
            @RequestParam(required = false) String analystId,
            @Parameter(description = "true = only my proposals")
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "20, 50 or 100")
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size,
            Actor actor) {
        return service.search(status, from, to, customerId, mine ? actor.id() : analystId, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "getProposal", summary = "Get a proposal", description = "Returns the version as ETag.")
    public ResponseEntity<ProposalResponse> get(@PathVariable UUID id) {
        ProposalResponse proposal = service.get(id);
        return ETags.ok(proposal, proposal.version());
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "getProposalHistory", summary = "Status timeline",
            description = "Every status change, oldest first, with who did it and the reason.")
    public List<StatusChangeResponse> history(@PathVariable UUID id) {
        return service.history(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "createProposal", summary = "Create a proposal",
            description = "Always created as DRAFT, owned by the caller.")
    public ResponseEntity<ProposalResponse> create(@Valid @RequestBody CreateProposalRequest request, Actor actor) {
        ProposalResponse created = service.create(request, actor);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).eTag(ETags.of(created.version())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "updateProposal", summary = "Edit a proposal", description = "Owner only, while DRAFT.")
    public ResponseEntity<ProposalResponse> update(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateProposalRequest request, Actor actor) {
        return withETag(service.update(id, ifMatch, request, actor));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "submitProposal", summary = "Submit for analysis", description = "Owner only. DRAFT → SUBMITTED.")
    public ResponseEntity<ProposalResponse> submit(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            Actor actor) {
        return withETag(service.transition(id, ProposalAction.SUBMIT, ifMatch, null, actor));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "cancelProposal", summary = "Cancel a draft", description = "Owner only. DRAFT → CANCELLED.")
    public ResponseEntity<ProposalResponse> cancel(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody(required = false) TransitionRequest request, Actor actor) {
        return withETag(service.transition(id, ProposalAction.CANCEL, ifMatch, reason(request), actor));
    }

    @PostMapping("/{id}/start-analysis")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(operationId = "startProposalAnalysis", summary = "Pick up a submitted proposal",
            description = "Manager, not the author. SUBMITTED → IN_ANALYSIS. From Phase 4 the AI worker does this "
                    + "automatically; this stays as the manual path when the worker is down.")
    public ResponseEntity<ProposalResponse> startAnalysis(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            Actor actor) {
        return withETag(service.transition(id, ProposalAction.START_ANALYSIS, ifMatch, null, actor));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(operationId = "approveProposal", summary = "Approve",
            description = "Manager, not the author (four-eyes). IN_ANALYSIS → APPROVED.")
    public ResponseEntity<ProposalResponse> approve(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody(required = false) TransitionRequest request, Actor actor) {
        return withETag(service.transition(id, ProposalAction.APPROVE, ifMatch, reason(request), actor));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(operationId = "rejectProposal", summary = "Reject",
            description = "Manager, not the author. Reason required. IN_ANALYSIS → REJECTED.")
    public ResponseEntity<ProposalResponse> reject(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody TransitionRequest request, Actor actor) {
        return withETag(service.transition(id, ProposalAction.REJECT, ifMatch, reason(request), actor));
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(operationId = "returnProposal", summary = "Return to the analyst for changes",
            description = "Manager, not the author. Reason required. IN_ANALYSIS → DRAFT (editable again).")
    public ResponseEntity<ProposalResponse> returnForChanges(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody TransitionRequest request, Actor actor) {
        return withETag(service.transition(id, ProposalAction.RETURN, ifMatch, reason(request), actor));
    }

    private static String reason(TransitionRequest request) {
        return request == null ? null : request.reason();
    }

    private static ResponseEntity<ProposalResponse> withETag(ProposalResponse proposal) {
        return ETags.ok(proposal, proposal.version());
    }
}
