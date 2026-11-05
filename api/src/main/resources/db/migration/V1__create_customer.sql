-- Customers: individuals (PF, identified by CPF) and companies (PJ, identified by CNPJ).
CREATE TABLE customer (
    id              UUID PRIMARY KEY,
    type            VARCHAR(2)     NOT NULL CHECK (type IN ('PF', 'PJ')),
    -- Normalized: CPF = 11 digits; CNPJ = 14 chars (alphanumeric CNPJ is valid from July 2026).
    -- Phase 2 encrypts this column and keeps a hash for lookups.
    document        VARCHAR(14)    NOT NULL,
    name            VARCHAR(150)   NOT NULL,
    email           VARCHAR(254)   NOT NULL,
    -- Monthly income (PF) or monthly revenue (PJ), in BRL
    monthly_revenue NUMERIC(15, 2) NOT NULL CHECK (monthly_revenue >= 0),
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    deleted_at      TIMESTAMPTZ,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT customer_document_length CHECK (
        (type = 'PF' AND char_length(document) = 11) OR (type = 'PJ' AND char_length(document) = 14)
    )
);

-- A document can be registered again only after the previous customer was deleted (soft delete).
CREATE UNIQUE INDEX customer_document_active_uk ON customer (document) WHERE deleted_at IS NULL;

-- Name search is "contains, case-insensitive"; pg_trgm makes that indexable.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX customer_name_trgm_idx ON customer USING gin (lower(name) gin_trgm_ops) WHERE deleted_at IS NULL;
