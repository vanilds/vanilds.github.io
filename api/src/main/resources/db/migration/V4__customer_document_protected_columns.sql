-- Phase 2, step 1 of 3: new columns for the protected CPF/CNPJ (nullable while existing rows are converted).
--   document_hash      HMAC-SHA256 (hex) — equality search and uniqueness
--   document_encrypted AES-256-GCM      — the value itself, readable only with the key
--   document_display   masked CPF / formatted CNPJ — what the screens show, no decryption needed
ALTER TABLE customer
    ADD COLUMN document_hash      VARCHAR(64),
    ADD COLUMN document_encrypted BYTEA,
    ADD COLUMN document_display   VARCHAR(20);
