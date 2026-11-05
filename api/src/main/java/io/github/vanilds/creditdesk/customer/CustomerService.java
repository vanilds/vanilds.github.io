package io.github.vanilds.creditdesk.customer;

import io.github.vanilds.creditdesk.audit.AuditAction;
import io.github.vanilds.creditdesk.audit.AuditService;
import io.github.vanilds.creditdesk.audit.Changes;
import io.github.vanilds.creditdesk.audit.Masking;
import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.validation.BrazilianDocuments;
import io.github.vanilds.creditdesk.shared.web.ApiException;
import io.github.vanilds.creditdesk.shared.web.ETags;
import io.github.vanilds.creditdesk.shared.web.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static io.github.vanilds.creditdesk.customer.CustomerRepository.Filters.active;
import static io.github.vanilds.creditdesk.customer.CustomerRepository.Filters.documentHashEquals;
import static io.github.vanilds.creditdesk.customer.CustomerRepository.Filters.nameContains;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    static final String AUDIT_ENTITY = "CUSTOMER";

    private final CustomerRepository customers;
    private final CustomerDocuments documents;
    private final List<CustomerDeletionGuard> deletionGuards;
    private final AuditService audit;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, CustomerDocuments documents,
                           List<CustomerDeletionGuard> deletionGuards, AuditService audit, Clock clock) {
        this.customers = customers;
        this.documents = documents;
        this.deletionGuards = deletionGuards;
        this.audit = audit;
        this.clock = clock;
    }

    public PageResponse<CustomerResponse> search(String document, String name, int page, int size) {
        String documentHash = documents.hash(BrazilianDocuments.normalize(blankToNull(document)));
        Specification<Customer> filter = Specification.allOf(
                active(), documentHashEquals(documentHash), nameContains(name));
        var pageRequest = PageResponse.request(page, size, Sort.by("name").and(Sort.by("id")));
        return PageResponse.of(customers.findAll(filter, pageRequest), CustomerResponse::from);
    }

    public CustomerResponse get(UUID id) {
        return CustomerResponse.from(findActive(id));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request, Actor actor) {
        String document = BrazilianDocuments.normalize(request.document());
        CustomerDocuments.Protected protectedDocument = documents.protect(request.type(), document);
        if (customers.existsByDocumentHashAndDeletedAtIsNull(protectedDocument.hash())) {
            throw ApiException.conflict("duplicate-document", "Duplicate document",
                    "A customer with this document already exists.");
        }
        var customer = new Customer(request.type(), protectedDocument, request.name().trim(), request.email().trim(),
                request.monthlyRevenue(), clock.instant());
        // saveAndFlush: a unique-index violation (race between two requests) surfaces here, inside the request,
        // and becomes a 409 instead of failing later at commit time.
        customers.saveAndFlush(customer);
        audit.record(actor, AuditAction.CUSTOMER_CREATED, AUDIT_ENTITY, customer.getId(), new Changes()
                .created("type", customer.getType())
                .created("document", customer.displayDocument())
                .created("name", customer.getName())
                .masked("email", null, Masking.email(customer.getEmail()))
                .created("monthlyRevenue", customer.getMonthlyRevenue()), null);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse update(UUID id, String ifMatch, UpdateCustomerRequest request, Actor actor) {
        Customer customer = findActive(id);
        ETags.require(ifMatch, customer.getVersion());
        var changes = new Changes()
                .field("name", customer.getName(), request.name().trim())
                .masked("email", Masking.email(customer.getEmail()), Masking.email(request.email().trim()))
                .field("monthlyRevenue", customer.getMonthlyRevenue(), request.monthlyRevenue());
        customer.update(request.name().trim(), request.email().trim(), request.monthlyRevenue(), clock.instant());
        customers.saveAndFlush(customer);
        audit.record(actor, AuditAction.CUSTOMER_UPDATED, AUDIT_ENTITY, id, changes, null);
        return CustomerResponse.from(customer);
    }

    @Transactional
    public void delete(UUID id, String ifMatch, Actor actor) {
        Customer customer = findActive(id);
        ETags.require(ifMatch, customer.getVersion());
        for (CustomerDeletionGuard guard : deletionGuards) {
            String reason = guard.blockingReason(id);
            if (reason != null) {
                throw ApiException.unprocessable("customer-has-open-proposals", "Customer cannot be deleted",
                        reason);
            }
        }
        customer.delete(clock.instant());
        customers.saveAndFlush(customer);
        audit.record(actor, AuditAction.CUSTOMER_DELETED, AUDIT_ENTITY, id, null, null);
    }

    /**
     * Decrypts the document for an auditor. A write transaction even though nothing in the customer changes:
     * the audit line is part of the operation, and if it cannot be saved the CPF must not be returned.
     */
    @Transactional
    public RevealedDocumentResponse revealDocument(UUID id, RevealDocumentRequest request, Actor actor) {
        // Deleted customers included: an investigation may be exactly about a removed customer
        Customer customer = customers.findById(id).orElseThrow(() -> ApiException.notFound("Customer", id));
        String document = documents.reveal(customer);
        audit.record(actor, AuditAction.CUSTOMER_DOCUMENT_REVEALED, AUDIT_ENTITY, id, null, request.reason().trim());
        return new RevealedDocumentResponse(id, customer.getType(), customer.getType().formatDocument(document));
    }

    /** Used by the proposal module. Deleted customers count as not found. */
    public Customer findActive(UUID id) {
        return customers.findByIdAndDeletedAtIsNull(id).orElseThrow(() -> ApiException.notFound("Customer", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
