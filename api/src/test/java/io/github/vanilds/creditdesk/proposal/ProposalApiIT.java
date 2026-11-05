package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProposalApiIT extends IntegrationTest {

    private UUID createProposal(UUID customerId) throws Exception {
        var result = postJson("/api/proposals", Map.of("customerId", customerId, "kind", "NEW_CREDIT",
                "amount", "15000.00", "installments", 24, "monthlyRate", "0.0199"))
                .andExpect(status().isCreated());
        return UUID.fromString(read(result).get("id").asText());
    }

    @Test
    void createsDraftWithComputedInstallment() throws Exception {
        UUID customerId = createCustomer("Fernanda Oliveira");

        postJson("/api/proposals", Map.of("customerId", customerId, "kind", "RENEGOTIATION",
                "amount", "15000.00", "installments", 24, "monthlyRate", "0.0199"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.kind").value("RENEGOTIATION"))
                .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                .andExpect(jsonPath("$.customerName").value("Fernanda Oliveira"))
                .andExpect(jsonPath("$.installmentAmount").value(792.17))
                .andExpect(jsonPath("$.totalAmount").value(19012.08));
    }

    @Test
    void rejectsUnknownCustomer() throws Exception {
        postJson("/api/proposals", Map.of("customerId", UUID.randomUUID(), "kind", "NEW_CREDIT",
                "amount", "1000.00", "installments", 12, "monthlyRate", "0.02"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validatesBusinessLimits() throws Exception {
        UUID customerId = createCustomer("Limits");
        postJson("/api/proposals", Map.of("customerId", customerId, "kind", "NEW_CREDIT",
                "amount", "50.00", "installments", 0, "monthlyRate", "0.5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field",
                        containsInAnyOrder("amount", "installments", "monthlyRate")));
    }

    @Test
    void editsDraftButNotSubmittedProposal() throws Exception {
        UUID id = createProposal(createCustomer("Editor"));
        String edit = """
                {"kind": "NEW_CREDIT", "amount": 18000.00, "installments": 36, "monthlyRate": 0.0189}
                """;

        mvc.perform(put("/api/proposals/{id}", id).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.installmentAmount").value(693.78))
                .andExpect(jsonPath("$.version").value(1));

        jdbc.update("update proposal set status = 'SUBMITTED' where id = ?", id);

        mvc.perform(put("/api/proposals/{id}", id).contentType(MediaType.APPLICATION_JSON).content(edit))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/proposal-not-editable"));
    }

    @Test
    void filtersByCustomerAndStatusWithinDefaultPeriod() throws Exception {
        UUID customerId = createCustomer("Filters");
        UUID draft = createProposal(customerId);
        UUID submitted = createProposal(customerId);
        createProposal(createCustomer("Other customer"));
        jdbc.update("update proposal set status = 'SUBMITTED' where id = ?", submitted);

        mvc.perform(get("/api/proposals").param("customerId", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)));

        mvc.perform(get("/api/proposals").param("customerId", customerId.toString()).param("status", "DRAFT"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(draft.toString()));
    }

    /** 23:30 in São Paulo on Nov 1st is already Nov 2nd in UTC — the filter must follow the business date. */
    @Test
    void periodFilterUsesSaoPauloDates() throws Exception {
        UUID customerId = createCustomer("Late Night");
        UUID id = createProposal(customerId);
        jdbc.update("update proposal set created_at = '2026-11-02T02:30:00Z' where id = ?", id);

        mvc.perform(get("/api/proposals").param("customerId", customerId.toString())
                        .param("from", "2026-11-01").param("to", "2026-11-01"))
                .andExpect(jsonPath("$.content", hasSize(1)));
        mvc.perform(get("/api/proposals").param("customerId", customerId.toString())
                        .param("from", "2026-11-02").param("to", "2026-11-02"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void periodIsAtMost90Days() throws Exception {
        LocalDate to = LocalDate.of(2026, 11, 4);
        mvc.perform(get("/api/proposals").param("from", to.minusDays(89).toString()).param("to", to.toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proposals").param("from", to.minusDays(90).toString()).param("to", to.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/invalid-period"));
        mvc.perform(get("/api/proposals").param("from", "2026-11-05").param("to", "2026-11-04"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidEnumInQueryIsProblem400() throws Exception {
        mvc.perform(get("/api/proposals").param("status", "MAYBE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
    }
}
