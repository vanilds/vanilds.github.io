package io.github.vanilds.creditdesk.proposal.opinion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/proposals/{id}/opinions")
@Tag(name = "AI opinions", description = "Credit opinions drafted by the AI worker after each submission")
public class OpinionController {

    private final OpinionService service;

    public OpinionController(OpinionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "listProposalOpinions", summary = "AI opinions of a proposal",
            description = "Newest first; empty while the worker is still drafting. The screen polls this after a "
                    + "submit. Advisory only: approving or rejecting is always a manager's decision.")
    public List<OpinionResponse> list(@PathVariable UUID id) {
        return service.list(id);
    }
}
