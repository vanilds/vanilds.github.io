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

    /** Startup data migrations that touch personal data (e.g. encrypting existing CPFs). */
    SYSTEM_MIGRATION
}
