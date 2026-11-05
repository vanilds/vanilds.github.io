package io.github.vanilds.creditdesk.proposal;

import io.github.vanilds.creditdesk.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProposalWorkflowIT extends IntegrationTest {

    private static final String PROBLEMS = "https://vanilds.github.io/problems/";

    /** A DRAFT created by ana, at version 0. */
    private UUID draftByAna() throws Exception {
        UUID customerId = createCustomer(unique("Workflow"));
        var result = postJson("/api/proposals", Map.of("customerId", customerId, "kind", "NEW_CREDIT",
                "amount", "20000.00", "installments", 24, "monthlyRate", "0.0199"))
                .andExpect(status().isCreated())
                .andExpect(header().string("ETag", "\"0\""));
        return UUID.fromString(read(result).get("id").asText());
    }

    private ResultActions act(UUID id, String action, long version, RequestPostProcessor user, String reason)
            throws Exception {
        var request = post("/api/proposals/{id}/{action}", id, action).with(user).header("If-Match", version(version));
        if (reason != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("reason", reason)));
        }
        return mvc.perform(request);
    }

    @Test
    void happyPathFromDraftToApproved() throws Exception {
        UUID id = draftByAna();

        act(id, "submit", 0, as(ANA), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(header().string("ETag", "\"1\""));
        act(id, "start-analysis", 1, as(MARCOS), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_ANALYSIS"));
        act(id, "approve", 2, as(MARCOS), "Good payment history").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mvc.perform(get("/api/proposals/{id}/history", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].to", contains("DRAFT", "SUBMITTED", "IN_ANALYSIS", "APPROVED")))
                .andExpect(jsonPath("$[*].actorName", contains("ana", "ana", "marcos", "marcos")))
                .andExpect(jsonPath("$[0].from").doesNotExist())
                .andExpect(jsonPath("$[3].reason").value("Good payment history"));
    }

    @Test
    void returnedProposalIsEditableAgainAndShowsTheReason() throws Exception {
        UUID id = draftByAna();
        act(id, "submit", 0, as(ANA), null);
        act(id, "start-analysis", 1, as(MARCOS), null);

        act(id, "return", 2, as(MARCOS), "Missing proof of income").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(put("/api/proposals/{id}", id).header("If-Match", version(3))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind": "NEW_CREDIT", "amount": 15000.00, "installments": 24, "monthlyRate": 0.0199}
                                """))
                .andExpect(status().isOk());
        mvc.perform(get("/api/proposals/{id}/history", id))
                .andExpect(jsonPath("$[3].reason").value("Missing proof of income"));
    }

    @Test
    void rejectAndReturnRequireAReason() throws Exception {
        UUID id = draftByAna();
        act(id, "submit", 0, as(ANA), null);
        act(id, "start-analysis", 1, as(MARCOS), null);

        act(id, "reject", 2, as(MARCOS), "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "reason-required"));
        act(id, "reject", 2, as(MARCOS), "Income does not support the installment").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void fourEyesManagerCannotDecideOwnProposal() throws Exception {
        UUID customerId = createCustomer(unique("Four eyes"));
        var created = mvc.perform(post("/api/proposals").with(as(MARCOS)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("customerId", customerId, "kind", "NEW_CREDIT",
                                "amount", "5000.00", "installments", 12, "monthlyRate", "0.02"))))
                .andExpect(status().isCreated());
        UUID id = UUID.fromString(read(created).get("id").asText());
        act(id, "submit", 0, as(MARCOS), null).andExpect(status().isOk());

        act(id, "start-analysis", 1, as(MARCOS), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "four-eyes"));
        // another manager can
        act(id, "start-analysis", 1, as(PAULA), null).andExpect(status().isOk());
    }

    @Test
    void onlyTheOwnerChangesADraft() throws Exception {
        UUID id = draftByAna();
        act(id, "submit", 0, as(BRUNO), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "not-proposal-owner"));
        mvc.perform(put("/api/proposals/{id}", id).with(as(BRUNO)).header("If-Match", version(0))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind": "NEW_CREDIT", "amount": 1.00e5, "installments": 24, "monthlyRate": 0.0199}
                                """))
                .andExpect(status().isForbidden());
        act(id, "cancel", 0, as(ANA), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void analystCannotDecide() throws Exception {
        UUID id = draftByAna();
        act(id, "submit", 0, as(ANA), null);
        act(id, "start-analysis", 1, as(BRUNO), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "forbidden"));
    }

    @Test
    void transitionNotAllowedFromCurrentStatusIs422() throws Exception {
        UUID id = draftByAna();
        act(id, "approve", 0, as(MARCOS), null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value(PROBLEMS + "invalid-transition"))
                .andExpect(jsonPath("$.detail").value("Cannot approve a proposal that is DRAFT; it must be IN_ANALYSIS."));
    }

    @Test
    void mineFilterShowsOnlyMyProposals() throws Exception {
        UUID customerId = createCustomer(unique("Mine"));
        postJson("/api/proposals", Map.of("customerId", customerId, "kind", "NEW_CREDIT", "amount", "1000.00",
                "installments", 12, "monthlyRate", "0.02")).andExpect(status().isCreated());
        mvc.perform(post("/api/proposals").with(as(BRUNO)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("customerId", customerId, "kind", "NEW_CREDIT",
                        "amount", "1000.00", "installments", 12, "monthlyRate", "0.02"))))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/proposals").with(as(BRUNO)).param("customerId", customerId.toString())
                        .param("mine", "true"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].analystName").value("bruno"));
    }
}
