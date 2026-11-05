package io.github.vanilds.creditdesk.customer.migration;

import io.github.vanilds.creditdesk.audit.AuditAction;
import io.github.vanilds.creditdesk.customer.CustomerDocuments;
import io.github.vanilds.creditdesk.customer.CustomerType;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Phase 2, step 2 of 3: encrypts the CPF/CNPJ of customers created before encryption existed.
 *
 * <p>A <b>Java</b> migration because SQL cannot do it: the key is not (and must not be) in the database. It is a
 * Spring bean, so Spring Boot hands it to Flyway with the encryption service injected, and Flyway runs it once,
 * in order, between V4 (new columns) and V6 (drop the plaintext column) — inside the same migration run, so
 * no instance of the API ever sees a half-converted table.
 *
 * <p>Each converted row gets an audit line: touching personal data in bulk is exactly what an auditor asks about.
 */
@Component
public class V5__Encrypt_customer_documents extends BaseJavaMigration {

    private static final String SYSTEM_ACTOR = "system:flyway";

    private final CustomerDocuments documents;

    public V5__Encrypt_customer_documents(CustomerDocuments documents) {
        this.documents = documents;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id, type, document FROM customer WHERE document_hash IS NULL");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE customer SET document_hash = ?, document_encrypted = ?, document_display = ? WHERE id = ?");
             PreparedStatement audit = connection.prepareStatement("""
                     INSERT INTO audit_log (id, occurred_at, actor_id, actor_name, action, entity_type, entity_id, reason)
                     VALUES (?, ?, ?, ?, ?, 'CUSTOMER', ?, 'Encrypted document at rest (Flyway V5)')""");
             ResultSet rows = select.executeQuery()) {
            Timestamp now = Timestamp.from(Instant.now());
            while (rows.next()) {
                UUID id = rows.getObject("id", UUID.class);
                CustomerDocuments.Protected document = documents.protect(
                        CustomerType.valueOf(rows.getString("type")), rows.getString("document"));
                update.setString(1, document.hash());
                update.setBytes(2, document.encrypted());
                update.setString(3, document.display());
                update.setObject(4, id);
                update.addBatch();

                audit.setObject(1, UUID.randomUUID());
                audit.setTimestamp(2, now);
                audit.setString(3, SYSTEM_ACTOR);
                audit.setString(4, SYSTEM_ACTOR);
                audit.setString(5, AuditAction.SYSTEM_MIGRATION.name());
                audit.setObject(6, id);
                audit.addBatch();
            }
            update.executeBatch();
            audit.executeBatch();
        }
    }
}
