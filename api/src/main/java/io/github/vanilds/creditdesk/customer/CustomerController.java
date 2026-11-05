package io.github.vanilds.creditdesk.customer;

import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.web.ETags;
import io.github.vanilds.creditdesk.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Individuals (PF) and companies (PJ)")
public class CustomerController {

    static final String IF_MATCH_DESCRIPTION = "ETag from the last read, e.g. \"3\". Required: missing → 428, stale → 412.";

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "searchCustomers", summary = "Search customers", description = "Paginated, ordered by name. CPFs are returned masked.")
    public PageResponse<CustomerResponse> search(
            @Parameter(description = "Exact CPF/CNPJ, with or without punctuation")
            @RequestParam(required = false) String document,
            @Parameter(description = "Part of the name, case-insensitive")
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "20, 50 or 100")
            @RequestParam(defaultValue = "" + PageResponse.DEFAULT_SIZE) int size) {
        return service.search(document, name, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ANALYST', 'AUDITOR')")
    @Operation(operationId = "getCustomer", summary = "Get a customer", description = "Returns the version as ETag.")
    public ResponseEntity<CustomerResponse> get(@PathVariable UUID id) {
        CustomerResponse customer = service.get(id);
        return ETags.ok(customer, customer.version());
    }

    @PostMapping
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "createCustomer", summary = "Register a customer", description = "Validates CPF/CNPJ check digits and rejects duplicates. The document is stored encrypted.")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request, Actor actor) {
        CustomerResponse created = service.create(request, actor);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).eTag(ETags.of(created.version())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ANALYST')")
    @Operation(operationId = "updateCustomer", summary = "Update name, e-mail and monthly revenue")
    public ResponseEntity<CustomerResponse> update(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH_DESCRIPTION) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody UpdateCustomerRequest request, Actor actor) {
        CustomerResponse updated = service.update(id, ifMatch, request, actor);
        return ETags.ok(updated, updated.version());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ANALYST')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteCustomer", summary = "Delete a customer", description = "Soft delete. Blocked while the customer has open proposals.")
    public void delete(
            @PathVariable UUID id,
            @Parameter(description = IF_MATCH_DESCRIPTION) @RequestHeader(name = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            Actor actor) {
        service.delete(id, ifMatch, actor);
    }

    @PostMapping("/{id}/document/reveal")
    @PreAuthorize("hasRole('AUDITOR')")
    @Operation(operationId = "revealCustomerDocument", summary = "Reveal the full CPF/CNPJ",
            description = "AUDITOR only. The reason is mandatory and recorded in the audit trail.")
    public RevealedDocumentResponse reveal(@PathVariable UUID id, @Valid @RequestBody RevealDocumentRequest request,
                                           Actor actor) {
        return service.revealDocument(id, request, actor);
    }
}
