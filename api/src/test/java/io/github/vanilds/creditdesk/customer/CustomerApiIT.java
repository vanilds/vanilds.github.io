package io.github.vanilds.creditdesk.customer;

import io.github.vanilds.creditdesk.IntegrationTest;
import io.github.vanilds.creditdesk.shared.validation.BrazilianDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomerApiIT extends IntegrationTest {

    @Test
    void createsIndividualAndReturnsMaskedCpf() throws Exception {
        String cpf = randomCpf();
        String formatted = BrazilianDocuments.formatCpf(cpf); // the API accepts punctuation

        var result = postJson("/api/customers", Map.of("type", "PF", "document", formatted,
                "name", "  Maria da Silva  ", "email", "maria@example.com", "monthlyRevenue", "8500.00"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/customers/")))
                .andExpect(jsonPath("$.type").value("PF"))
                .andExpect(jsonPath("$.document").value(BrazilianDocuments.maskCpf(cpf)))
                .andExpect(jsonPath("$.name").value("Maria da Silva"))
                .andExpect(jsonPath("$.monthlyRevenue").value(8500.00))
                .andExpect(jsonPath("$.version").value(0));

        // stored normalized (digits only), never the masked form
        String id = read(result).get("id").asText();
        assertThat(jdbc.queryForObject("select document from customer where id = ?::uuid", String.class, id))
                .isEqualTo(cpf);
    }

    @Test
    void acceptsAlphanumericCnpjAndShowsItFormatted() throws Exception {
        jdbc.update("update customer set deleted_at = now() where document = '12ABC34501DE35'"); // re-runnable
        postJson("/api/customers", Map.of("type", "PJ", "document", "12.abc.345/01de-35",
                "name", "Studio Pixel Tecnologia Ltda", "email", "adm@example.com", "monthlyRevenue", "320000"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.document").value("12.ABC.345/01DE-35"));
    }

    @Test
    void rejectsInvalidCpfWithFieldError() throws Exception {
        postJson("/api/customers", Map.of("type", "PF", "document", "529.982.247-24",
                "name", "Maria", "email", "maria@example.com", "monthlyRevenue", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/validation"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("document"))
                .andExpect(jsonPath("$.errors[0].message").value("invalid CPF"));
    }

    @Test
    void rejectsCnpjSentAsIndividual() throws Exception {
        postJson("/api/customers", Map.of("type", "PF", "document", "11.222.333/0001-81",
                "name", "Mixed Up", "email", "x@example.com", "monthlyRevenue", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("invalid CPF"));
    }

    @Test
    void reportsEveryInvalidField() throws Exception {
        postJson("/api/customers", Map.of("type", "PF", "document", randomCpf(),
                "name", "A", "email", "not-an-email", "monthlyRevenue", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void rejectsDuplicateDocument() throws Exception {
        String cpf = randomCpf();
        var body = Map.of("type", "PF", "document", cpf, "name", "First", "email", "a@example.com",
                "monthlyRevenue", "100");
        postJson("/api/customers", body).andExpect(status().isCreated());

        postJson("/api/customers", Map.of("type", "PF", "document", BrazilianDocuments.formatCpf(cpf),
                "name", "Second", "email", "b@example.com", "monthlyRevenue", "100"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/duplicate-document"));
    }

    @Test
    void searchesByNameAndDocument() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String cpf = randomCpf();
        postJson("/api/customers", Map.of("type", "PF", "document", cpf, "name", "Ana " + tag + " Souza",
                "email", "ana@example.com", "monthlyRevenue", "100")).andExpect(status().isCreated());
        createCustomer("Bruno " + tag + " Lima");
        createCustomer("Someone else entirely");

        mvc.perform(get("/api/customers").param("name", tag.toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].name").value("Ana " + tag + " Souza")) // ordered by name
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.size").value(20));

        mvc.perform(get("/api/customers").param("document", BrazilianDocuments.formatCpf(cpf)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].document").value(BrazilianDocuments.maskCpf(cpf)));
    }

    @Test
    void nameSearchTreatsWildcardsAsText() throws Exception {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        createCustomer("Loja " + tag + " 100% Feliz");
        createCustomer("Loja " + tag + " 1000 Feliz");

        mvc.perform(get("/api/customers").param("name", tag + " 100%"))
                .andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    void onlyAllowsPageSizes20To100() throws Exception {
        mvc.perform(get("/api/customers").param("size", "30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'size' must be 20, 50 or 100."));
        mvc.perform(get("/api/customers").param("size", "50")).andExpect(status().isOk());
    }

    @Test
    void updatesEditableFieldsAndIncrementsVersion() throws Exception {
        UUID id = createCustomer("Carlos Antigo");

        mvc.perform(put("/api/customers/{id}", id).contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Carlos Novo", "email": "novo@example.com", "monthlyRevenue": 9200.50}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Carlos Novo"))
                .andExpect(jsonPath("$.monthlyRevenue").value(9200.50))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    void softDeletesAndFreesTheDocument() throws Exception {
        String cpf = randomCpf();
        var body = Map.of("type", "PF", "document", cpf, "name", "Temporary", "email", "t@example.com",
                "monthlyRevenue", "100");
        String id = read(postJson("/api/customers", body)).get("id").asText();

        mvc.perform(delete("/api/customers/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/customers/{id}", id)).andExpect(status().isNotFound());

        // the row is still there for history
        assertThat(jdbc.queryForObject("select deleted_at is not null from customer where id = ?::uuid",
                Boolean.class, id)).isTrue();
        // and the same document can be registered again
        postJson("/api/customers", body).andExpect(status().isCreated());
    }

    @Test
    void cannotDeleteCustomerWithOpenProposal() throws Exception {
        UUID id = createCustomer("Has Draft");
        postJson("/api/proposals", Map.of("customerId", id, "kind", "NEW_CREDIT", "amount", "5000.00",
                "installments", 12, "monthlyRate", "0.0199")).andExpect(status().isCreated());

        mvc.perform(delete("/api/customers/{id}", id))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type")
                        .value("https://vanilds.github.io/problems/customer-has-open-proposals"));
    }

    @Test
    void unknownCustomerIsProblem404() throws Exception {
        mvc.perform(get("/api/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/not-found"))
                .andExpect(jsonPath("$.instance").exists());
    }

    @Test
    void malformedJsonIsProblem400() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }
}
