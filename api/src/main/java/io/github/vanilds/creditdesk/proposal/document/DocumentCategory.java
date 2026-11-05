package io.github.vanilds.creditdesk.proposal.document;

/** What a file proves. The AI worker only ever sees these categories, never the files. */
public enum DocumentCategory {
    /** Pay slip, income tax return (PF) */
    INCOME_PROOF,
    /** Balance sheet, revenue statement (PJ) */
    FINANCIAL_STATEMENT,
    BANK_STATEMENT,
    IDENTITY,
    ADDRESS_PROOF,
    /** The debt being renegotiated: contract, statement of the current balance */
    DEBT_STATEMENT,
    OTHER
}
