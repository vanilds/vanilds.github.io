-- Phase 4: transactional outbox, idempotent consumers, AI credit opinions and proposal documents.

-- Transactional outbox. The event is written in the SAME transaction as the business change (e.g. the proposal
-- becoming SUBMITTED), so "changed but never announced" and "announced but rolled back" are both impossible.
-- OutboxRelay publishes pending rows to RabbitMQ and marks them; delivery is at-least-once.
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,               -- becomes the AMQP message_id: consumers deduplicate on it
    aggregate_type VARCHAR(30)  NOT NULL,
    aggregate_id   UUID         NOT NULL,
    event_type     VARCHAR(60)  NOT NULL,          -- also the routing key, e.g. proposal.submitted
    payload        JSONB        NOT NULL,
    correlation_id VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL,
    published_at   TIMESTAMPTZ,                    -- null = still to publish
    attempts       INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500)
);
-- Partial index: the relay only ever looks for pending rows, and this index stays tiny however big the table gets.
CREATE INDEX outbox_event_pending_idx ON outbox_event (created_at) WHERE published_at IS NULL;

-- Inbox for idempotent consumers: RabbitMQ may deliver the same message twice (redelivery after a crash, a
-- retried publish). The primary key turns "already processed?" into an atomic insert.
CREATE TABLE processed_message (
    consumer     VARCHAR(60) NOT NULL,
    message_id   UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (consumer, message_id)
);

-- Credit opinion drafted by the AI worker. Advisory only: the decision stays with a manager.
CREATE TABLE proposal_opinion (
    id               UUID PRIMARY KEY,             -- the opinion.drafted event id (deterministic in the worker)
    proposal_id      UUID         NOT NULL REFERENCES proposal (id),
    proposal_version BIGINT       NOT NULL,        -- the submitted version it refers to
    recommendation   VARCHAR(20)  NOT NULL,        -- FAVORABLE, CAUTION, UNFAVORABLE
    risk_level       VARCHAR(10)  NOT NULL,        -- LOW, MEDIUM, HIGH
    summary_en       TEXT         NOT NULL,
    summary_pt       TEXT         NOT NULL,
    risk_flags       JSONB        NOT NULL,
    indicators       JSONB        NOT NULL,
    suggested_plan   JSONB,
    model            VARCHAR(100) NOT NULL,        -- which model (or the rules template) wrote the text
    generated_at     TIMESTAMPTZ  NOT NULL,
    received_at      TIMESTAMPTZ  NOT NULL
);
CREATE INDEX proposal_opinion_proposal_idx ON proposal_opinion (proposal_id, received_at DESC);

-- Files attached to a proposal. The bytes live in S3 (MinIO locally); this row is the metadata and the state.
CREATE TABLE proposal_document (
    id               UUID PRIMARY KEY,
    proposal_id      UUID         NOT NULL REFERENCES proposal (id),
    category         VARCHAR(30)  NOT NULL,
    file_name        VARCHAR(200) NOT NULL,
    content_type     VARCHAR(100) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    storage_key      VARCHAR(200) NOT NULL UNIQUE, -- proposals/{proposalId}/{documentId}: no file name in the key
    status           VARCHAR(20)  NOT NULL,        -- PENDING_UPLOAD → AVAILABLE
    uploaded_by_id   VARCHAR(100) NOT NULL,
    uploaded_by_name VARCHAR(100) NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    confirmed_at     TIMESTAMPTZ
);
CREATE INDEX proposal_document_proposal_idx ON proposal_document (proposal_id, created_at);
