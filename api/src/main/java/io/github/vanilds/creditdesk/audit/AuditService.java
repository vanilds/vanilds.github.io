package io.github.vanilds.creditdesk.audit;

import io.github.vanilds.creditdesk.shared.security.Actor;
import io.github.vanilds.creditdesk.shared.web.CorrelationIdFilter;
import io.github.vanilds.creditdesk.shared.web.PageResponse;
import io.github.vanilds.creditdesk.shared.web.Period;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static io.github.vanilds.creditdesk.audit.AuditRepository.Filters.equalTo;
import static io.github.vanilds.creditdesk.audit.AuditRepository.Filters.occurredBetween;

/**
 * Services call {@link #record} explicitly, right next to the change they make.
 *
 * <p><b>Same transaction, on purpose</b> ({@code MANDATORY}): if the audit insert fails, the business change rolls
 * back too, and vice versa. There is never an approved proposal without its audit line, nor an audit line for
 * something that did not happen. (Events for other services follow the same idea: see messaging/Outbox.)
 *
 * <p><b>Why explicit calls instead of Hibernate Envers or entity listeners?</b> Envers records <i>rows</i> that
 * changed; an audit trail needs <i>intent</i>: "approved", "revealed CPF because of ticket 123", who and from which
 * request. That only exists in the service.
 */
@Service
@Transactional(readOnly = true)
public class AuditService {

    private final AuditRepository entries;
    private final Clock clock;
    private final ZoneId businessZone;

    public AuditService(AuditRepository entries, Clock clock, ZoneId businessZone) {
        this.entries = entries;
        this.clock = clock;
        this.businessZone = businessZone;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Actor actor, AuditAction action, String entityType, UUID entityId, Changes changes,
                       String reason) {
        Map<String, Object> diff = changes == null ? null : changes.asMap();
        entries.save(new AuditEntry(clock.instant(), actor.id(), actor.username(), action, entityType, entityId,
                diff, reason, CorrelationIdFilter.current()));
    }

    public PageResponse<AuditEntryResponse> search(String entityType, UUID entityId, String actorId,
                                                   AuditAction action, LocalDate from, LocalDate to, int page,
                                                   int size) {
        Period period = Period.of(from, to, clock, businessZone);
        Specification<AuditEntry> filter = Specification.allOf(
                occurredBetween(period.start(), period.endExclusive()),
                equalTo("entityType", entityType),
                equalTo("entityId", entityId),
                equalTo("actorId", actorId),
                equalTo("action", action));
        var pageRequest = PageResponse.request(page, size,
                Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        return PageResponse.of(entries.findAll(filter, pageRequest), AuditEntryResponse::from);
    }
}
