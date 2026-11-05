package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/proposals")
@Tag(name = "Proposals", description = "New credit and renegotiation proposals")
public class ProposalController {

    private final ProposalService service;

    public ProposalController(ProposalService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(operationId = "searchProposals", summary = "Search proposals",
            description = "Newest first. The period is at most 90 days and defaults to the last 30 days.")
    public PageResponse<ProposalResponse> search(
            @RequestParam(required = false) ProposalStatus status,
            @Parameter(description = "First day, inclusive (São Paulo date), e.g. 2026-11-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Last day, inclusive (São Paulo date). Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "20, 50 or 100")
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return service.search(status, from, to, customerId, page, size);
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getProposal", summary = "Get a proposal")
    public ProposalResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(operationId = "createProposal", summary = "Create a proposal", description = "Always created as DRAFT.")
    public ResponseEntity<ProposalResponse> create(@Valid @RequestBody CreateProposalRequest request) {
        ProposalResponse created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateProposal", summary = "Edit a proposal", description = "Only while it is DRAFT.")
    public ProposalResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProposalRequest request) {
        return service.update(id, request);
    }
}
