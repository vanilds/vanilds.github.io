package io.github.vanilds.creditdesk.audit;

import io.github.vanilds.creditdesk.IntegrationTest;
import io.github.vanilds.creditdesk.shared.validation.BrazilianDocuments;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuditIT extends IntegrationTest {

    private UUID createCustomerWithCpf(String cpf, String email) throws Exception {
        var result = mvc.perform(post("/api/customers").header("X-Correlation-Id", "test-corr-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "PF", "document", cpf,
                                "name", unique("Audited"), "email", email, "monthlyRevenue", "3000.00"))))
                .andExpect(status().isCreated());
        return UUID.fromString(read(result).get("id").asText());
    }

    @Test
    void recordsWhoWhatAndFromWhichRequestWithPersonalDataMasked() throws Exception {
        String cpf = randomCpf();
        UUID id = createCustomerWithCpf(cpf, "joana.prado@example.com");

        var audit = mvc.perform(get("/api/audit").with(as(ALINE))
                        .param("entityType", "CUSTOMER").param("entityId", id.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action").value("CUSTOMER_CREATED"))
                .andExpect(jsonPath("$.content[0].actorId").value(ANA.id()))
                .andExpect(jsonPath("$.content[0].actorName").value("ana"))
                .andExpect(jsonPath("$.content[0].correlationId").value("test-corr-0001"))
                .andExpect(jsonPath("$.content[0].changes.document.to").value(BrazilianDocuments.maskCpf(cpf)))
                .andExpect(jsonPath("$.content[0].changes.email.to").value("j**********@example.com"));
        assertThat(audit.andReturn().getResponse().getContentAsString()).doesNotContain(cpf, "joana.prado");
    }

    @Test
    void updateRecordsOnlyWhatChanged() throws Exception {
        UUID id = createCustomer(unique("Diff"));
        mvc.perform(put("/api/customers/{id}", id).header("If-Match", version(0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Diff Renamed", "email": "test@example.com", "monthlyRevenue": 5000.0}
                                """))
                .andExpect(status().isOk());

        mvc.perform(get("/api/audit").with(as(MARCOS)).param("entityId", id.toString())
                        .param("action", "CUSTOMER_UPDATED"))
                .andExpect(jsonPath("$.content[0].changes.name.to").value("Diff Renamed"))
                // same e-mail, and 5000.0 == 5000.00: not a change
                .andExpect(jsonPath("$.content[0].changes.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].changes.monthlyRevenue").doesNotExist());
    }

    @Test
    void analystCannotReadTheAuditTrail() throws Exception {
        mvc.perform(get("/api/audit")).andExpect(status().isForbidden());
    }

    @Test
    void auditorRevealsDocumentOnlyWithReasonAndIsAudited() throws Exception {
        String cpf = randomCpf();
        UUID id = createCustomerWithCpf(cpf, "r@example.com");

        mvc.perform(post("/api/customers/{id}/document/reveal", id).with(as(ALINE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/customers/{id}/document/reveal", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Ticket SAC-1234 identity\"}"))
                .andExpect(status().isForbidden()); // analysts never see the full CPF

        mvc.perform(post("/api/customers/{id}/document/reveal", id).with(as(ALINE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Ticket SAC-1234 identity\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").value(BrazilianDocuments.formatCpf(cpf)));

        mvc.perform(get("/api/audit").with(as(ALINE)).param("entityId", id.toString())
                        .param("action", "CUSTOMER_DOCUMENT_REVEALED"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].actorName").value("aline"))
                .andExpect(jsonPath("$.content[0].reason").value("Ticket SAC-1234 identity"));
    }

    @Test
    void auditTrailIsAppendOnlyInTheDatabase() throws Exception {
        UUID id = createCustomer(unique("Immutable"));
        assertThatThrownBy(() -> jdbc.update("update audit_log set actor_name = 'someone else' where entity_id = ?", id))
                .rootCause().hasMessageContaining("audit_log is append-only: UPDATE is not allowed");
        assertThatThrownBy(() -> jdbc.update("delete from audit_log where entity_id = ?", id))
                .rootCause().hasMessageContaining("audit_log is append-only: DELETE is not allowed");
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void failedBusinessRuleLeavesNoAuditLine() throws Exception {
        UUID id = createCustomer(unique("Rollback"));
        postJson("/api/proposals", Map.of("customerId", id, "kind", "NEW_CREDIT", "amount", "5000.00",
                "installments", 12, "monthlyRate", "0.0199")).andExpect(status().isCreated());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/customers/{id}", id).header("If-Match", version(0)))
                .andExpect(status().isUnprocessableEntity());

        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity_id = ? and action = 'CUSTOMER_DELETED'",
                Integer.class, id)).isZero();
    }
}
