-- Phase 2, step 3 of 3 (step 2 is the Java migration V5, which encrypted every existing row).
-- From here on the database has no readable CPF anywhere.
ALTER TABLE customer
    ALTER COLUMN document_hash SET NOT NULL,
    ALTER COLUMN document_encrypted SET NOT NULL,
    ALTER COLUMN document_display SET NOT NULL;

DROP INDEX customer_document_active_uk;
ALTER TABLE customer DROP CONSTRAINT customer_document_length; -- the length rule now lives in the validator
ALTER TABLE customer DROP COLUMN document;

-- Same rule as before (one active customer per document), now on the hash
CREATE UNIQUE INDEX customer_document_hash_active_uk ON customer (document_hash) WHERE deleted_at IS NULL;
