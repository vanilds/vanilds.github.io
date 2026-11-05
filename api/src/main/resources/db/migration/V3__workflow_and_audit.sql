-- Phase 2: proposal ownership, status timeline and the append-only audit trail.

ALTER TABLE proposal ADD COLUMN analyst_name VARCHAR(100);
CREATE INDEX proposal_analyst_created_at_idx ON proposal (analyst_id, created_at DESC);

-- Timeline of each proposal, read by the analyst ("returned by marcos: missing proof of income").
CREATE TABLE proposal_status_history (
    id          UUID PRIMARY KEY,
    proposal_id UUID         NOT NULL REFERENCES proposal (id),
    from_status VARCHAR(20),                  -- null = the proposal was created
    to_status   VARCHAR(20)  NOT NULL,
    actor_id    VARCHAR(100) NOT NULL,
    actor_name  VARCHAR(100) NOT NULL,
    reason      VARCHAR(500),
    changed_at  TIMESTAMPTZ  NOT NULL
);
CREATE INDEX proposal_status_history_proposal_idx ON proposal_status_history (proposal_id, changed_at);

-- Who did what, when, from which request, and why. Personal data inside "changes" is masked by the API.
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor_id       VARCHAR(100) NOT NULL,
    actor_name     VARCHAR(100) NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    entity_type    VARCHAR(20)  NOT NULL,
    entity_id      UUID         NOT NULL,
    changes        JSONB,
    reason         VARCHAR(500),
    correlation_id VARCHAR(64)
);
CREATE INDEX audit_log_occurred_at_idx ON audit_log (occurred_at DESC);
CREATE INDEX audit_log_entity_idx ON audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (actor_id, occurred_at DESC);

-- Append-only, enforced by the database itself: even a bug, a script or someone with the application's DB
-- password cannot rewrite history through UPDATE or DELETE. (TRUNCATE is also blocked.)
-- A real bank would also ship these rows to separate, write-once storage (e.g. S3 Object Lock) — see Phase 7.
CREATE FUNCTION reject_history_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only: % is not allowed', TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION reject_history_change();

CREATE TRIGGER proposal_status_history_append_only
    BEFORE UPDATE OR DELETE ON proposal_status_history
    FOR EACH ROW EXECUTE FUNCTION reject_history_change();
