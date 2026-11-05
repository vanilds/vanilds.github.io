package io.github.vanilds.creditdesk.audit;

import io.github.vanilds.creditdesk.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit")
@Tag(name = "Audit", description = "Append-only audit trail (MANAGER and AUDITOR)")
public class AuditController {

    private final AuditService service;

    public AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('MANAGER', 'AUDITOR')")
    @Operation(operationId = "searchAudit", summary = "Search the audit trail",
            description = "Newest first. The period is at most 90 days and defaults to the last 30 days.")
    public PageResponse<AuditEntryResponse> search(
            @Parameter(description = "CUSTOMER or PROPOSAL")
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) UUID entityId,
            @Parameter(description = "Keycloak user id")
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "20, 50 or 100")
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return service.search(entityType, entityId, actorId, action, from, to, page, size);
    }
}
