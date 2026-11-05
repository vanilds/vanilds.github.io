package io.github.vanilds.creditdesk.customer.migration;

import io.github.vanilds.creditdesk.customer.CustomerDocuments;
import io.github.vanilds.creditdesk.customer.CustomerType;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

/**
 * Synthetic demo data, profiles {@code dev} (docker compose) and {@code demo} (the Kubernetes clusters, local and
 * public). Every name is invented and every CPF/CNPJ is a generated test number with valid check digits.
 * {@code demo} only adds this seed: unlike {@code dev} it brings no throwaway keys or local S3 defaults.
 *
 * <p>Java instead of SQL because the documents must be encrypted with the application key, like real data.
 * Repeatable ("R__"): Flyway runs it after the versioned migrations and again whenever {@link #getChecksum()}
 * changes. Fixed ids + ON CONFLICT DO NOTHING keep it safe to re-run (also when someone already registered one
 * of these documents by hand: the unique index on the hash wins and the seed row is skipped). Dates are relative to now(), so the default
 * 30-day window always shows data.
 *
 * <p>Owners are the demo users of infra/keycloak/creditdesk-realm.json (fixed ids there): proposals belong to the
 * analysts ana and bruno, decisions were taken by the manager marcos.
 */
@Component
@Profile({"dev", "demo"})
public class R__Demo_data extends BaseJavaMigration {

    public static final String ANA = "a0000000-0000-4000-8000-000000000001";
    public static final String BRUNO = "a0000000-0000-4000-8000-000000000002";
    public static final String MARCOS = "a0000000-0000-4000-8000-000000000003";

    /** Bump when the data below changes, so Flyway re-runs the seed. */
    private static final int VERSION = 2;

    private record SeedCustomer(String id, CustomerType type, String document, String name, String email,
                                String monthlyRevenue, int daysAgo) {
    }

    private static final List<SeedCustomer> CUSTOMERS = List.of(
            new SeedCustomer("645e281a-b688-534d-a9ee-9862ef1d894c", CustomerType.PF, "10433218100", "Maria Aparecida Souza", "maria.souza@example.com", "6800.00", 90),
            new SeedCustomer("288a260a-2d74-5265-b938-044e3bd208b1", CustomerType.PF, "96001338914", "João Pedro Almeida", "joao.almeida@example.com", "4200.00", 89),
            new SeedCustomer("57c75a92-32bd-5c38-88e1-cc82efd486f3", CustomerType.PF, "08386379499", "Ana Beatriz Ferreira", "ana.ferreira@example.com", "12500.00", 88),
            new SeedCustomer("551070be-bc84-57a4-be49-a92f0f354eda", CustomerType.PF, "02654235114", "Carlos Eduardo Lima", "carlos.lima@example.com", "3100.00", 87),
            new SeedCustomer("6de4faa7-1a33-5297-ad1d-2fbd040f5e67", CustomerType.PF, "16155940789", "Fernanda Oliveira Santos", "fernanda.santos@example.com", "9300.00", 86),
            new SeedCustomer("f1be42cb-1e75-5176-9f93-49f276ba1d2e", CustomerType.PF, "81618495950", "Rafael Gomes Ribeiro", "rafael.ribeiro@example.com", "15800.00", 85),
            new SeedCustomer("65dc5ceb-5a93-5fb1-a12f-46ed21055ee7", CustomerType.PF, "31034131656", "Juliana Martins Costa", "juliana.costa@example.com", "5400.00", 84),
            new SeedCustomer("5ba44343-20e1-5b4d-8ca9-a718052da2b7", CustomerType.PF, "47525534144", "Lucas Rocha Carvalho", "lucas.carvalho@example.com", "7700.00", 83),
            new SeedCustomer("34989ddb-783f-5db5-bceb-04411cf41458", CustomerType.PJ, "92832764000161", "Padaria Pão Dourado Ltda", "financeiro@paodourado.example.com", "180000.00", 82),
            new SeedCustomer("40147046-11fb-5df9-8e14-b74c58a546f6", CustomerType.PJ, "83503056000196", "Oficina Mecânica Torque Ltda", "contato@torque.example.com", "95000.00", 81),
            new SeedCustomer("afc5d8e7-bee8-5e8b-82b2-928a8a2c9a6e", CustomerType.PJ, "41395376000120", "Distribuidora Vale Verde S.A.", "credito@valeverde.example.com", "2400000.00", 80),
            new SeedCustomer("44e0a6ed-8ae3-520d-a033-b472ea611713", CustomerType.PJ, "12ABC34501DE35", "Studio Pixel Tecnologia Ltda", "adm@studiopixel.example.com", "320000.00", 79)
    );

    private static final String SEEDED_PROPOSALS = """
            '2fcf2245-f7b7-58d0-b419-f7ca367e203e', '14d00419-dbce-553d-b205-58d58bc59e8f', '9cbd1171-6bd5-5dee-985b-378320b891b9', 'f3d052cb-8e55-5f13-87f8-6c44d59ca41b', '0216e5e3-6080-5ddc-946c-90f7fada99a0', '140b36a9-d056-5430-a8e4-96bbc2d39452', '78bb48d1-5ca0-5dc5-8261-10519af088f8', '534bd8ac-3d1d-5de8-b188-8bea92a4b324', 'c0fa1a7f-3197-5fec-839e-95e68a86eb11', '12eb4341-615c-5c64-90f0-fc46afcfdd14', 'ca7bd217-8ddd-512c-b9cf-6b7484e12d75', 'a18ee401-998e-5899-b7bd-cc0d8b5b5ba5', '24c68b7f-60c2-5855-9357-ec9421e87d1f', 'f92b7e7a-03ca-5475-8a54-fa3dcdfe2202', '6e6f8819-df52-5f64-81d9-56a6e274d998', '06f437e2-e64d-5b18-aefe-ffa6d8bf24d6', '2eacbbbb-4897-5d64-81a1-b465d0b3ae39', 'cb00c023-84d3-5117-b5b9-d26e56012a5f', 'ec0ad02d-c977-5f35-8d94-5ef02b4859af', 'ab83c673-ab78-5323-b359-6b4d955f6017', '5030bb0c-30ca-5483-bd4c-7b6f3d251b26', '8223e19a-ff2b-5bb3-ab92-7cf30015b7c1', '7ff4da91-551a-590a-880f-eebbcbd41045', '1b3df8dc-3e12-5de3-97e6-f2d0dbb640a5', '43a50011-2eb8-574f-b023-d7f0e96defe8'""".strip();

    private static final String PROPOSALS = """
            INSERT INTO proposal (id, customer_id, kind, amount, installments, monthly_rate, status, created_at, updated_at)
            SELECT v.id::uuid, v.customer_id::uuid, v.kind, v.amount, v.installments, v.monthly_rate, v.status,
                   v.created_at, v.updated_at
            FROM (VALUES
                ('2fcf2245-f7b7-58d0-b419-f7ca367e203e', 'afc5d8e7-bee8-5e8b-82b2-928a8a2c9a6e', 'NEW_CREDIT', 480000.00, 36, 0.0249, 'SUBMITTED', now() - interval '38 days 15 hours', now() - interval '38 days 15 hours'),
                ('14d00419-dbce-553d-b205-58d58bc59e8f', '34989ddb-783f-5db5-bceb-04411cf41458', 'NEW_CREDIT', 250000.00, 60, 0.0149, 'CANCELLED', now() - interval '43 days 16 hours', now() - interval '43 days 16 hours'),
                ('9cbd1171-6bd5-5dee-985b-378320b891b9', '6de4faa7-1a33-5297-ad1d-2fbd040f5e67', 'RENEGOTIATION', 40000.00, 12, 0.0199, 'APPROVED', now() - interval '27 days 10 hours', now() - interval '27 days 10 hours'),
                ('f3d052cb-8e55-5f13-87f8-6c44d59ca41b', '5ba44343-20e1-5b4d-8ca9-a718052da2b7', 'RENEGOTIATION', 5000.00, 48, 0.0189, 'DRAFT', now() - interval '32 days 9 hours', now() - interval '32 days 9 hours'),
                ('0216e5e3-6080-5ddc-946c-90f7fada99a0', 'afc5d8e7-bee8-5e8b-82b2-928a8a2c9a6e', 'NEW_CREDIT', 250000.00, 18, 0.0199, 'IN_ANALYSIS', now() - interval '48 days 10 hours', now() - interval '48 days 10 hours'),
                ('140b36a9-d056-5430-a8e4-96bbc2d39452', '34989ddb-783f-5db5-bceb-04411cf41458', 'RENEGOTIATION', 50000.00, 36, 0.0149, 'IN_ANALYSIS', now() - interval '7 days 13 hours', now() - interval '7 days 13 hours'),
                ('78bb48d1-5ca0-5dc5-8261-10519af088f8', '6de4faa7-1a33-5297-ad1d-2fbd040f5e67', 'NEW_CREDIT', 8000.00, 18, 0.0329, 'DRAFT', now() - interval '5 days 9 hours', now() - interval '5 days 9 hours'),
                ('534bd8ac-3d1d-5de8-b188-8bea92a4b324', '44e0a6ed-8ae3-520d-a033-b472ea611713', 'NEW_CREDIT', 480000.00, 48, 0.0189, 'APPROVED', now() - interval '8 days 18 hours', now() - interval '8 days 18 hours'),
                ('c0fa1a7f-3197-5fec-839e-95e68a86eb11', '5ba44343-20e1-5b4d-8ca9-a718052da2b7', 'NEW_CREDIT', 25000.00, 24, 0.0329, 'APPROVED', now() - interval '55 days 17 hours', now() - interval '55 days 17 hours'),
                ('12eb4341-615c-5c64-90f0-fc46afcfdd14', '65dc5ceb-5a93-5fb1-a12f-46ed21055ee7', 'NEW_CREDIT', 8000.00, 60, 0.0199, 'DRAFT', now() - interval '25 days 18 hours', now() - interval '25 days 18 hours'),
                ('ca7bd217-8ddd-512c-b9cf-6b7484e12d75', 'afc5d8e7-bee8-5e8b-82b2-928a8a2c9a6e', 'RENEGOTIATION', 250000.00, 48, 0.0249, 'DRAFT', now() - interval '7 days 11 hours', now() - interval '7 days 11 hours'),
                ('a18ee401-998e-5899-b7bd-cc0d8b5b5ba5', '551070be-bc84-57a4-be49-a92f0f354eda', 'RENEGOTIATION', 5000.00, 12, 0.0329, 'DRAFT', now() - interval '35 days 11 hours', now() - interval '35 days 11 hours'),
                ('24c68b7f-60c2-5855-9357-ec9421e87d1f', '40147046-11fb-5df9-8e14-b74c58a546f6', 'NEW_CREDIT', 120000.00, 12, 0.0149, 'DRAFT', now() - interval '14 days 9 hours', now() - interval '14 days 9 hours'),
                ('f92b7e7a-03ca-5475-8a54-fa3dcdfe2202', '645e281a-b688-534d-a9ee-9862ef1d894c', 'NEW_CREDIT', 12000.00, 48, 0.0189, 'SUBMITTED', now() - interval '17 days 18 hours', now() - interval '17 days 18 hours'),
                ('6e6f8819-df52-5f64-81d9-56a6e274d998', '5ba44343-20e1-5b4d-8ca9-a718052da2b7', 'NEW_CREDIT', 8000.00, 60, 0.0329, 'CANCELLED', now() - interval '36 days 15 hours', now() - interval '36 days 15 hours'),
                ('06f437e2-e64d-5b18-aefe-ffa6d8bf24d6', '551070be-bc84-57a4-be49-a92f0f354eda', 'RENEGOTIATION', 15000.00, 18, 0.0149, 'IN_ANALYSIS', now() - interval '6 days 18 hours', now() - interval '6 days 18 hours'),
                ('2eacbbbb-4897-5d64-81a1-b465d0b3ae39', '65dc5ceb-5a93-5fb1-a12f-46ed21055ee7', 'RENEGOTIATION', 12000.00, 36, 0.0249, 'REJECTED', now() - interval '55 days 19 hours', now() - interval '55 days 19 hours'),
                ('cb00c023-84d3-5117-b5b9-d26e56012a5f', '645e281a-b688-534d-a9ee-9862ef1d894c', 'NEW_CREDIT', 40000.00, 12, 0.0249, 'APPROVED', now() - interval '46 days 13 hours', now() - interval '46 days 13 hours'),
                ('ec0ad02d-c977-5f35-8d94-5ef02b4859af', '288a260a-2d74-5265-b938-044e3bd208b1', 'NEW_CREDIT', 8000.00, 18, 0.0329, 'APPROVED', now() - interval '28 days 10 hours', now() - interval '28 days 10 hours'),
                ('ab83c673-ab78-5323-b359-6b4d955f6017', '65dc5ceb-5a93-5fb1-a12f-46ed21055ee7', 'RENEGOTIATION', 8000.00, 36, 0.0189, 'SUBMITTED', now() - interval '55 days 9 hours', now() - interval '55 days 9 hours'),
                ('5030bb0c-30ca-5483-bd4c-7b6f3d251b26', '5ba44343-20e1-5b4d-8ca9-a718052da2b7', 'NEW_CREDIT', 25000.00, 12, 0.0329, 'REJECTED', now() - interval '53 days 8 hours', now() - interval '53 days 8 hours'),
                ('8223e19a-ff2b-5bb3-ab92-7cf30015b7c1', '288a260a-2d74-5265-b938-044e3bd208b1', 'NEW_CREDIT', 8000.00, 36, 0.0249, 'SUBMITTED', now() - interval '30 days 11 hours', now() - interval '30 days 11 hours'),
                ('7ff4da91-551a-590a-880f-eebbcbd41045', '65dc5ceb-5a93-5fb1-a12f-46ed21055ee7', 'NEW_CREDIT', 5000.00, 36, 0.0149, 'DRAFT', now() - interval '24 days 12 hours', now() - interval '24 days 12 hours'),
                ('1b3df8dc-3e12-5de3-97e6-f2d0dbb640a5', '5ba44343-20e1-5b4d-8ca9-a718052da2b7', 'RENEGOTIATION', 12000.00, 60, 0.0329, 'IN_ANALYSIS', now() - interval '42 days 19 hours', now() - interval '42 days 19 hours'),
                ('43a50011-2eb8-574f-b023-d7f0e96defe8', '5ba44343-20e1-5b4d-8ca9-a718052da2b7', 'NEW_CREDIT', 8000.00, 24, 0.0189, 'REJECTED', now() - interval '3 days 17 hours', now() - interval '3 days 17 hours')
            -- a seed customer skipped because its document was already registered by hand: skip its proposals too
            ) AS v (id, customer_id, kind, amount, installments, monthly_rate, status, created_at, updated_at)
            WHERE EXISTS (SELECT 1 FROM customer c WHERE c.id = v.customer_id::uuid)
            ON CONFLICT (id) DO NOTHING;
            """;

    /** Ana owns proposals with an even first hex digit, Bruno the others — both analysts have work to show. */
    private static final String OWNERS = """
            UPDATE proposal SET
                analyst_id   = CASE WHEN mod(('x' || substr(id::text, 1, 1))::bit(4)::int, 2) = 0 THEN '%1$s' ELSE '%2$s' END,
                analyst_name = CASE WHEN mod(('x' || substr(id::text, 1, 1))::bit(4)::int, 2) = 0 THEN 'ana' ELSE 'bruno' END
            WHERE analyst_id IS NULL AND id IN (%3$s)
            """.formatted(ANA, BRUNO, SEEDED_PROPOSALS);

    /** A creation line for every seeded proposal, plus the move to its current status. */
    private static final String HISTORY = """
            INSERT INTO proposal_status_history (id, proposal_id, from_status, to_status, actor_id, actor_name, reason, changed_at)
            SELECT md5(p.id::text || ':created')::uuid, p.id, NULL, 'DRAFT', p.analyst_id, p.analyst_name, NULL, p.created_at
            FROM proposal p WHERE p.analyst_id IN ('%1$s', '%2$s')
            ON CONFLICT (id) DO NOTHING;

            INSERT INTO proposal_status_history (id, proposal_id, from_status, to_status, actor_id, actor_name, reason, changed_at)
            SELECT md5(p.id::text || ':status')::uuid, p.id,
                   CASE WHEN p.status IN ('SUBMITTED', 'CANCELLED') THEN 'DRAFT' WHEN p.status = 'IN_ANALYSIS' THEN 'SUBMITTED' ELSE 'IN_ANALYSIS' END,
                   p.status,
                   CASE WHEN p.status IN ('SUBMITTED', 'CANCELLED') THEN p.analyst_id ELSE '%3$s' END,
                   CASE WHEN p.status IN ('SUBMITTED', 'CANCELLED') THEN p.analyst_name ELSE 'marcos' END,
                   CASE WHEN p.status = 'REJECTED' THEN 'Installment above 30%% of monthly income' END,
                   p.created_at + interval '2 hours'
            FROM proposal p WHERE p.analyst_id IN ('%1$s', '%2$s') AND p.status <> 'DRAFT'
            ON CONFLICT (id) DO NOTHING;
            """.formatted(ANA, BRUNO, MARCOS);

    private final CustomerDocuments documents;

    public R__Demo_data(CustomerDocuments documents) {
        this.documents = documents;
    }

    @Override
    public Integer getChecksum() {
        return VERSION;
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO customer (id, type, document_hash, document_encrypted, document_display, name, email,
                                      monthly_revenue, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, now() - make_interval(days => ?), now() - make_interval(days => ?))
                ON CONFLICT DO NOTHING""")) {
            for (SeedCustomer customer : CUSTOMERS) {
                CustomerDocuments.Protected document = documents.protect(customer.type(), customer.document());
                insert.setObject(1, UUID.fromString(customer.id()));
                insert.setString(2, customer.type().name());
                insert.setString(3, document.hash());
                insert.setBytes(4, document.encrypted());
                insert.setString(5, document.display());
                insert.setString(6, customer.name());
                insert.setString(7, customer.email());
                insert.setBigDecimal(8, new BigDecimal(customer.monthlyRevenue()));
                insert.setInt(9, customer.daysAgo());
                insert.setInt(10, customer.daysAgo());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(PROPOSALS);
            statement.execute(OWNERS);
            statement.execute(HISTORY);
        }
    }
}
