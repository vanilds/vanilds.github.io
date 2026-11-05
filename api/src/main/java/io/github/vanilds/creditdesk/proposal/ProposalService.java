package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.customer.Customer;
import io.github.vanilds.creditdesk.customer.CustomerService;
import io.github.vanilds.creditdesk.shared.web.ApiException;
import io.github.vanilds.creditdesk.shared.web.PageResponse;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static io.github.vanilds.creditdesk.proposal.ProposalRepository.Filters.createdBetween;
import static io.github.vanilds.creditdesk.proposal.ProposalRepository.Filters.customerEquals;
import static io.github.vanilds.creditdesk.proposal.ProposalRepository.Filters.statusEquals;

@Service
@Transactional(readOnly = true)
public class ProposalService {

    /** The grid never scans more than 90 days: keeps queries on the created_at index fast and predictable. */
    static final int MAX_WINDOW_DAYS = 90;
    static final int DEFAULT_WINDOW_DAYS = 30;

    private final ProposalRepository proposals;
    private final CustomerService customers;
    private final Clock clock;
    private final ZoneId businessZone;

    public ProposalService(ProposalRepository proposals, CustomerService customers, Clock clock,
                           ZoneId businessZone) {
        this.proposals = proposals;
        this.customers = customers;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    /**
     * {@code from} and {@code to} are São Paulo calendar dates, both inclusive. Defaults to the last 30 days.
     */
    public PageResponse<ProposalResponse> search(ProposalStatus status, LocalDate from, LocalDate to,
                                                 UUID customerId, int page, int size) {
        LocalDate end = to != null ? to : LocalDate.now(clock.withZone(businessZone));
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_WINDOW_DAYS - 1L);
        if (start.isAfter(end)) {
            throw ApiException.badRequest("invalid-period", "Invalid period", "'from' must be on or before 'to'.");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_WINDOW_DAYS) {
            throw ApiException.badRequest("invalid-period", "Invalid period",
                    "The period can be at most %d days.".formatted(MAX_WINDOW_DAYS));
        }

        Specification<Proposal> filter = Specification.allOf(
                createdBetween(start.atStartOfDay(businessZone).toInstant(),
                        end.plusDays(1).atStartOfDay(businessZone).toInstant()),
                statusEquals(status),
                customerEquals(customerId));
        // id as a tie-breaker: without it, rows with the same timestamp can jump between pages
        var pageRequest = PageResponse.request(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(proposals.findAll(filter, pageRequest), ProposalResponse::from);
    }

    public ProposalResponse get(UUID id) {
        return ProposalResponse.from(find(id));
    }

    @Transactional
    public ProposalResponse create(CreateProposalRequest request) {
        Customer customer = customers.findActive(request.customerId());
        var proposal = Proposal.draft(customer, request.kind(), request.amount(), request.installments(),
                request.monthlyRate(), clock.instant());
        return ProposalResponse.from(proposals.saveAndFlush(proposal));
    }

    @Transactional
    public ProposalResponse update(UUID id, UpdateProposalRequest request) {
        Proposal proposal = find(id);
        proposal.update(request.kind(), request.amount(), request.installments(), request.monthlyRate(),
                clock.instant());
        return ProposalResponse.from(proposals.saveAndFlush(proposal));
    }

    private Proposal find(UUID id) {
        return proposals.findWithCustomerById(id).orElseThrow(() -> ApiException.notFound("Proposal", id));
    }
}
