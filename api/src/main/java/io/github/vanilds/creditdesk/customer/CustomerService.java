package io.github.vanilds.creditdesk.customer;

import io.github.vanilds.creditdesk.shared.validation.BrazilianDocuments;
import io.github.vanilds.creditdesk.shared.web.ApiException;
import io.github.vanilds.creditdesk.shared.web.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static io.github.vanilds.creditdesk.customer.CustomerRepository.Filters.active;
import static io.github.vanilds.creditdesk.customer.CustomerRepository.Filters.documentEquals;
import static io.github.vanilds.creditdesk.customer.CustomerRepository.Filters.nameContains;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customers;
    private final List<CustomerDeletionGuard> deletionGuards;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, List<CustomerDeletionGuard> deletionGuards, Clock clock) {
        this.customers = customers;
        this.deletionGuards = deletionGuards;
        this.clock = clock;
    }

    public PageResponse<CustomerResponse> search(String document, String name, int page, int size) {
        Specification<Customer> filter = Specification.allOf(
                active(), documentEquals(BrazilianDocuments.normalize(blankToNull(document))), nameContains(name));
        var pageRequest = PageResponse.request(page, size, Sort.by("name").and(Sort.by("id")));
        return PageResponse.of(customers.findAll(filter, pageRequest), CustomerResponse::from);
    }

    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(findActive(id));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String document = BrazilianDocuments.normalize(request.document());
        if (customers.existsByDocumentAndDeletedAtIsNull(document)) {
            throw ApiException.conflict("duplicate-document", "Duplicate document",
                    "A customer with this document already exists.");
        }
        var customer = new Customer(request.type(), document, request.name().trim(), request.email().trim(),
                request.monthlyRevenue(), clock.instant());
        // saveAndFlush: a unique-index violation (race between two requests) surfaces here, inside the request,
        // and becomes a 409 instead of failing later at commit time.
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    @Transactional
    public CustomerResponse update(UUID id, UpdateCustomerRequest request) {
        Customer customer = findActive(id);
        customer.update(request.name().trim(), request.email().trim(), request.monthlyRevenue(), clock.instant());
        return CustomerResponse.from(customers.saveAndFlush(customer));
    }

    @Transactional
    public void delete(UUID id) {
        Customer customer = findActive(id);
        for (CustomerDeletionGuard guard : deletionGuards) {
            String reason = guard.blockingReason(id);
            if (reason != null) {
                throw ApiException.unprocessable("customer-has-open-proposals", "Customer cannot be deleted",
                        reason);
            }
        }
        customer.delete(clock.instant());
    }

    /** Used by the proposal module. Deleted customers count as not found. */
    public Customer findActive(UUID id) {
        return customers.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Customer", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
