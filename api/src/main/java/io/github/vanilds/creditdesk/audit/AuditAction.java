package io.github.vanilds.creditdesk.audit;

/** Everything that changes data, plus every time someone sees an unmasked CPF. */
public enum AuditAction {
    CUSTOMER_CREATED,
    CUSTOMER_UPDATED,
    CUSTOMER_DELETED,
    /** Reading is audited only here: an auditor saw a full CPF, and had to say why. */
    CUSTOMER_DOCUMENT_REVEALED,

    PROPOSAL_CREATED,
    PROPOSAL_UPDATED,
    PROPOSAL_SUBMITTED,
    PROPOSAL_CANCELLED,
    PROPOSAL_ANALYSIS_STARTED,
    PROPOSAL_APPROVED,
    PROPOSAL_REJECTED,
    PROPOSAL_RETURNED,
    /** The AI worker's credit opinion arrived (advisory; the decision stays with a manager). */
    PROPOSAL_OPINION_RECORDED,
    PROPOSAL_DOCUMENT_ADDED,
    /** Reading is audited here too: someone opened a file with personal financial data (LGPD). */
    PROPOSAL_DOCUMENT_DOWNLOADED,

    /** Startup data migrations that touch personal data (e.g. encrypting existing CPFs). */
    SYSTEM_MIGRATION
}
