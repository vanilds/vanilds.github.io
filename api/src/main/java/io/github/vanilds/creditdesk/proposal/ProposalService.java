package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.audit.AuditAction;
import io.github.vanilds.creditdesk.audit.AuditService;
import io.github.vanilds.creditdesk.audit.Changes;
import io.github.vanilds.creditdesk.customer.Customer;
import io.github.vanilds.creditdesk.customer.CustomerService;
import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.web.ApiException;
import io.github.vanilds.creditdesk.shared.web.ETags;
import io.github.vanilds.creditdesk.shared.web.PageResponse;
import io.github.vanilds.creditdesk.shared.web.Period;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static io.github.vanilds.creditdesk.proposal.ProposalRepository.Filters.analystEquals;
import static io.github.vanilds.creditdesk.proposal.ProposalRepository.Filters.createdBetween;
import static io.github.vanilds.creditdesk.proposal.ProposalRepository.Filters.customerEquals;
import static io.github.vanilds.creditdesk.proposal.ProposalRepository.Filters.statusEquals;

@Service
@Transactional(readOnly = true)
public class ProposalService {

    static final String AUDIT_ENTITY = "PROPOSAL";

    private final ProposalRepository proposals;
    private final ProposalStatusChangeRepository history;
    private final CustomerService customers;
    private final AuditService audit;
    private final Clock clock;
    private final ZoneId businessZone;

    public ProposalService(ProposalRepository proposals, ProposalStatusChangeRepository history,
                           CustomerService customers, AuditService audit, Clock clock, ZoneId businessZone) {
        this.proposals = proposals;
        this.history = history;
        this.customers = customers;
        this.audit = audit;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    /**
     * {@code from} and {@code to} are São Paulo calendar dates, both inclusive. Defaults to the last 30 days,
     * at most 90 (the grid never scans more, so queries stay on the created_at index).
     */
    public PageResponse<ProposalResponse> search(ProposalStatus status, LocalDate from, LocalDate to,
                                                 UUID customerId, String analystId, int page, int size) {
        Period period = Period.of(from, to, clock, businessZone);
        Specification<Proposal> filter = Specification.allOf(
                createdBetween(period.start(), period.endExclusive()),
                statusEquals(status),
                customerEquals(customerId),
                analystEquals(analystId));
        // id as a tie-breaker: without it, rows with the same timestamp can jump between pages
        var pageRequest = PageResponse.request(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(proposals.findAll(filter, pageRequest), ProposalResponse::from);
    }

    public ProposalResponse get(UUID id) {
        return ProposalResponse.from(find(id));
    }

    public List<StatusChangeResponse> history(UUID id) {
        find(id); // 404 for an unknown proposal instead of an empty list
        return history.findByProposalIdOrderByChangedAtAscIdAsc(id).stream().map(StatusChangeResponse::from).toList();
    }

    @Transactional
    public ProposalResponse create(CreateProposalRequest request, Actor actor) {
        Customer customer = customers.findActive(request.customerId());
        Instant now = clock.instant();
        var proposal = Proposal.draft(customer, request.kind(), request.amount(), request.installments(),
                request.monthlyRate(), actor, now);
        proposals.saveAndFlush(proposal);
        history.save(new ProposalStatusChange(proposal.getId(), null, proposal.getStatus(), actor.id(),
                actor.username(), null, now));
        audit.record(actor, AuditAction.PROPOSAL_CREATED, AUDIT_ENTITY, proposal.getId(), new Changes()
                .created("customerId", customer.getId())
                .created("kind", proposal.getKind())
                .created("amount", proposal.getAmount())
                .created("installments", proposal.getInstallments())
                .created("monthlyRate", proposal.getMonthlyRate()), null);
        return ProposalResponse.from(proposal);
    }

    @Transactional
    public ProposalResponse update(UUID id, String ifMatch, UpdateProposalRequest request, Actor actor) {
        Proposal proposal = find(id);
        ETags.require(ifMatch, proposal.getVersion());
        var changes = new Changes()
                .field("kind", proposal.getKind(), request.kind())
                .field("amount", proposal.getAmount(), request.amount())
                .field("installments", proposal.getInstallments(), request.installments())
                .field("monthlyRate", proposal.getMonthlyRate(), request.monthlyRate());
        proposal.update(request.kind(), request.amount(), request.installments(), request.monthlyRate(), actor,
                clock.instant());
        proposals.saveAndFlush(proposal);
        audit.record(actor, AuditAction.PROPOSAL_UPDATED, AUDIT_ENTITY, id, changes, null);
        return ProposalResponse.from(proposal);
    }

    /**
     * One transaction for the three writes: the new status, the history line and the audit line. Either all of
     * them happen or none — there is no "approved but not audited" state. Phase 4 adds a fourth write here,
     * the outbox row that tells RabbitMQ about the submission.
     */
    @Transactional
    public ProposalResponse transition(UUID id, ProposalAction action, String ifMatch, String rawReason,
                                       Actor actor) {
        Proposal proposal = find(id);
        ETags.require(ifMatch, proposal.getVersion());
        String reason = rawReason == null || rawReason.isBlank() ? null : rawReason.trim();
        Instant now = clock.instant();
        ProposalStatus previous = proposal.apply(action, actor, reason, now);
        proposals.saveAndFlush(proposal);
        history.save(new ProposalStatusChange(id, previous, proposal.getStatus(), actor.id(), actor.username(),
                reason, now));
        audit.record(actor, action.auditAction, AUDIT_ENTITY, id,
                new Changes().field("status", previous, proposal.getStatus()), reason);
        return ProposalResponse.from(proposal);
    }

    private Proposal find(UUID id) {
        return proposals.findWithCustomerById(id).orElseThrow(() -> ApiException.notFound("Proposal", id));
    }
}
