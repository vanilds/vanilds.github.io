-- Credit proposals (new credit or renegotiation of an existing debt).
CREATE TABLE proposal (
    id            UUID PRIMARY KEY,
    customer_id   UUID           NOT NULL REFERENCES customer (id),
    kind          VARCHAR(20)    NOT NULL CHECK (kind IN ('NEW_CREDIT', 'RENEGOTIATION')),
    amount        NUMERIC(15, 2) NOT NULL CHECK (amount > 0),
    installments  INTEGER        NOT NULL CHECK (installments BETWEEN 1 AND 120),
    -- Monthly interest rate as a fraction: 0.0199 = 1.99% a month
    monthly_rate  NUMERIC(7, 6)  NOT NULL CHECK (monthly_rate >= 0 AND monthly_rate <= 0.2),
    status        VARCHAR(20)    NOT NULL CHECK (status IN
                      ('DRAFT', 'SUBMITTED', 'IN_ANALYSIS', 'APPROVED', 'REJECTED', 'CANCELLED')),
    analyst_id    VARCHAR(100),  -- filled from the JWT subject in Phase 2
    created_at    TIMESTAMPTZ    NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL,
    version       BIGINT         NOT NULL DEFAULT 0
);

-- The proposals screen always filters by period (and often status), newest first.
CREATE INDEX proposal_created_at_idx ON proposal (created_at DESC);
CREATE INDEX proposal_status_created_at_idx ON proposal (status, created_at DESC);
CREATE INDEX proposal_customer_idx ON proposal (customer_id);
