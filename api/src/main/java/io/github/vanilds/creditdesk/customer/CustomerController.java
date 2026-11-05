package io.github.vanilds.creditdesk.customer;

import io.github.vanilds.creditdesk.shared.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
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
    @Operation(operationId = "getCustomer", summary = "Get a customer")
    public CustomerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @Operation(operationId = "createCustomer", summary = "Register a customer", description = "Validates CPF/CNPJ check digits and rejects duplicates.")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = service.create(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").build(created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @Operation(operationId = "updateCustomer", summary = "Update name, e-mail and monthly revenue")
    public CustomerResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateCustomerRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteCustomer", summary = "Delete a customer", description = "Soft delete. Blocked while the customer has open proposals.")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
