package io.github.vanilds.creditdesk.customer.migration;

import io.github.vanilds.creditdesk.IntegrationTest;
import io.github.vanilds.creditdesk.customer.CustomerDocuments;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * The seed only runs in the dev profile, so nothing else would notice if its SQL broke. Runs it (twice, it must
 * be re-runnable) against the test database and checks the result through the API.
 */
class DemoDataIT extends IntegrationTest {

    @Autowired
    DataSource dataSource;

    @Autowired
    CustomerDocuments documents;

    @Test
    void seedRunsTwiceAndProducesEncryptedOwnedData() throws Exception {
        // other tests may have registered one of the seed's documents (the alphanumeric CNPJ): free them first
        jdbc.update("update customer set deleted_at = now() where deleted_at is null and document_display in "
                + "('12.ABC.345/01DE-35', '41.395.376/0001-20') and id not in "
                + "('44e0a6ed-8ae3-520d-a033-b472ea611713', 'afc5d8e7-bee8-5e8b-82b2-928a8a2c9a6e')");
        var seed = new R__Demo_data(documents);
        for (int run = 0; run < 2; run++) {
            try (Connection connection = dataSource.getConnection()) {
                seed.migrate(new Context() {
                    @Override
                    public Configuration getConfiguration() {
                        return null;
                    }

                    @Override
                    public Connection getConnection() {
                        return connection;
                    }
                });
            }
        }

        assertThat(jdbc.queryForObject("select count(*) from proposal where analyst_id in (?, ?)", Integer.class,
                R__Demo_data.ANA, R__Demo_data.BRUNO)).isEqualTo(25);
        assertThat(jdbc.queryForObject("select count(*) from proposal_status_history where proposal_id = "
                + "'9cbd1171-6bd5-5dee-985b-378320b891b9'", Integer.class)).isEqualTo(2);
        // the PJ with alphanumeric CNPJ is findable by document, i.e. its hash was computed with the app key
        mvc.perform(get("/api/customers").param("document", "41.395.376/0001-20"))
                .andExpect(jsonPath("$.content[0].name").value("Distribuidora Vale Verde S.A."));
    }
}
