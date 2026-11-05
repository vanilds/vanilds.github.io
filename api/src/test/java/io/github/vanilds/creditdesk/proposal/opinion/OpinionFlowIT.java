package io.github.vanilds.creditdesk.proposal.opinion;

import io.github.vanilds.creditdesk.IntegrationTest;
import io.github.vanilds.creditdesk.messaging.MessagingTestSupport;
import io.github.vanilds.creditdesk.messaging.Topology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API's side of the AI flow: it receives opinion.drafted exactly as the worker publishes it. */
class OpinionFlowIT extends IntegrationTest {

    private static final Duration WAIT = Duration.ofSeconds(15);

    @Autowired
    RabbitTemplate rabbit;

    private UUID submittedByAna() throws Exception {
        UUID customerId = createCustomer(unique("Opinion"));
        UUID id = UUID.fromString(read(postJson("/api/proposals", Map.of("customerId", customerId,
                "kind", "NEW_CREDIT", "amount", "20000.00", "installments", 24, "monthlyRate", "0.0199"))).get("id").asText());
        mvc.perform(post("/api/proposals/{id}/submit", id).header("If-Match", version(0))).andExpect(status().isOk());
        return id;
    }

    private static String opinion(UUID proposalId, long version) {
        return """
                {"proposalId": "%s", "proposalVersion": %d, "recommendation": "CAUTION", "riskLevel": "MEDIUM",
                 "summary": {"en": "Installment takes 34%% of income.", "pt": "A parcela compromete 34%% da renda."},
                 "riskFlags": [{"code": "HIGH_INSTALLMENT_TO_INCOME", "severity": "MEDIUM",
                                "message": "Installment above 30%% of monthly income"}],
                 "indicators": {"installmentToIncome": 0.34},
                 "suggestedPlan": {"installments": 36, "installmentAmount": 785.10, "installmentToIncome": 0.29},
                 "model": "rules-template-v1", "generatedAt": "2026-11-05T12:00:00Z", "unknownFutureField": true}
                """.formatted(proposalId, version);
    }

    /** Publishes like the worker: JSON body, message id = opinion id, persistent. */
    private void publish(UUID opinionId, String body) {
        var properties = new MessageProperties();
        properties.setMessageId(opinionId == null ? null : opinionId.toString());
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setType(Topology.OPINION_DRAFTED);
        rabbit.send(Topology.EVENTS, Topology.OPINION_DRAFTED,
                new Message(body.getBytes(StandardCharsets.UTF_8), properties));
    }

    private String statusOf(UUID id) throws Exception {
        return read(mvc.perform(get("/api/proposals/{id}", id))).get("status").asText();
    }

    private int opinionCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM proposal_opinion WHERE proposal_id = ?", Integer.class, id);
    }

    @Test
    void opinionIsStoredAndMovesTheProposalToAnalysis() throws Exception {
        UUID id = submittedByAna();
        UUID opinionId = UUID.randomUUID();

        publish(opinionId, opinion(id, 1));

        await().atMost(WAIT).until(() -> statusOf(id).equals("IN_ANALYSIS"));
        mvc.perform(get("/api/proposals/{id}/opinions", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(opinionId.toString()))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[0].recommendation").value("CAUTION"))
                .andExpect(jsonPath("$[0].summary.pt").value("A parcela compromete 34% da renda."))
                .andExpect(jsonPath("$[0].riskFlags[0].code").value("HIGH_INSTALLMENT_TO_INCOME"))
                .andExpect(jsonPath("$[0].suggestedPlan.installments").value(36));
        mvc.perform(get("/api/proposals/{id}/history", id))
                .andExpect(jsonPath("$[*].to", contains("DRAFT", "SUBMITTED", "IN_ANALYSIS")))
                .andExpect(jsonPath("$[2].actorName").value("ai-worker"))
                .andExpect(jsonPath("$[2].reason").value("AI credit opinion ready"));
        mvc.perform(get("/api/audit").with(as(ALINE)).param("entityId", id.toString()))
                .andExpect(jsonPath("$.content[?(@.action == 'PROPOSAL_OPINION_RECORDED')].actorId")
                        .value("system:ai-worker"));

        // The decision is still a person's: the manager approves as usual (version 2 after the worker's move)
        mvc.perform(post("/api/proposals/{id}/approve", id).with(as(MARCOS)).header("If-Match", version(2)))
                .andExpect(status().isOk());
    }

    @Test
    void aDuplicateDeliveryChangesNothing() throws Exception {
        UUID id = submittedByAna();
        UUID opinionId = UUID.randomUUID();
        publish(opinionId, opinion(id, 1));
        await().atMost(WAIT).until(() -> opinionCount(id) == 1);

        publish(opinionId, opinion(id, 1)); // at-least-once delivery: the same message again

        await().during(Duration.ofSeconds(2)).atMost(WAIT).until(() -> opinionCount(id) == 1);
        mvc.perform(get("/api/proposals/{id}/history", id)).andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void aLateOpinionIsKeptButDoesNotMoveTheProposal() throws Exception {
        UUID id = submittedByAna();
        // The worker was slow: a manager picked the proposal up by hand first (version 1 → 2)
        mvc.perform(post("/api/proposals/{id}/start-analysis", id).with(as(MARCOS)).header("If-Match", version(1)))
                .andExpect(status().isOk());

        publish(UUID.randomUUID(), opinion(id, 1));

        await().atMost(WAIT).until(() -> opinionCount(id) == 1);
        mvc.perform(get("/api/proposals/{id}/history", id))
                .andExpect(jsonPath("$[*].actorName", contains("ana", "ana", "marcos")));
        mvc.perform(get("/api/proposals/{id}/opinions", id)).andExpect(jsonPath("$[0].current").value(true));
    }

    @Test
    void anOpinionForAnEarlierSubmissionIsNotCurrent() throws Exception {
        UUID id = submittedByAna();
        mvc.perform(post("/api/proposals/{id}/start-analysis", id).with(as(MARCOS)).header("If-Match", version(1)));
        mvc.perform(post("/api/proposals/{id}/return", id).with(as(MARCOS)).header("If-Match", version(2))
                .contentType("application/json").content("{\"reason\": \"Missing proof of income\"}"));
        mvc.perform(post("/api/proposals/{id}/submit", id).header("If-Match", version(3))).andExpect(status().isOk());

        publish(UUID.randomUUID(), opinion(id, 1)); // drafted for the first submission, arrives now

        await().atMost(WAIT).until(() -> opinionCount(id) == 1);
        assertThat(statusOf(id)).isEqualTo("SUBMITTED"); // still waiting for the opinion on version 4
    }

    @Test
    void poisonMessagesGoStraightToTheDeadLetterQueue() throws Exception {
        UUID id = submittedByAna();
        UUID badJson = UUID.randomUUID();
        UUID invalid = UUID.randomUUID();
        UUID unknownProposal = UUID.randomUUID();

        publish(badJson, "{not json");
        publish(invalid, opinion(id, 1).replace("\"riskLevel\": \"MEDIUM\",", ""));
        publish(unknownProposal, opinion(UUID.randomUUID(), 1));

        for (UUID messageId : new UUID[]{badJson, invalid, unknownProposal}) {
            Message dead = MessagingTestSupport.receiveMatching(rabbit, Topology.OPINIONS_QUEUE + ".dlq",
                    m -> messageId.toString().equals(m.getMessageProperties().getMessageId()), WAIT);
            assertThat(dead.getMessageProperties().<String>getHeader("x-dead-letter-reason")).isNotBlank();
            // poison is not retried: it never went through the retry queue
            assertThat(dead.getMessageProperties().getXDeathHeader()).isNullOrEmpty();
        }
        assertThat(opinionCount(id)).isZero();
        assertThat(statusOf(id)).isEqualTo("SUBMITTED");
    }

    @Test
    void aFailureThatKeepsHappeningIsRetriedThenParked() throws Exception {
        UUID id = submittedByAna();
        UUID opinionId = UUID.randomUUID();
        // Sabotage: a row with the same primary key already exists, so every attempt fails on insert
        jdbc.update("""
                INSERT INTO proposal_opinion (id, proposal_id, proposal_version, recommendation, risk_level, summary_en,
                    summary_pt, risk_flags, indicators, model, generated_at, received_at)
                VALUES (?, ?, 1, 'FAVORABLE', 'LOW', 'x', 'x', '[]', '{}', 'test', now(), now())""", opinionId, id);

        publish(opinionId, opinion(id, 1));

        Message dead = MessagingTestSupport.receiveMatching(rabbit, Topology.OPINIONS_QUEUE + ".dlq",
                m -> opinionId.toString().equals(m.getMessageProperties().getMessageId()), WAIT);
        // max-attempts is 3 in tests: rejected twice into the retry queue, parked on the third failure
        assertThat(dead.getMessageProperties().getXDeathHeader())
                .anySatisfy(death -> {
                    assertThat(death.get("queue")).isEqualTo(Topology.OPINIONS_QUEUE);
                    assertThat(death.get("count")).isEqualTo(2L);
                });
        assertThat(dead.getMessageProperties().<String>getHeader("x-dead-letter-reason")).contains("3 attempts");
        assertThat(statusOf(id)).isEqualTo("SUBMITTED"); // each failed attempt rolled back completely
    }

    @Test
    void aMessageWithoutIdIsDeadLettered() throws Exception {
        UUID id = submittedByAna();
        publish(null, opinion(id, 1));

        Message dead = MessagingTestSupport.receiveMatching(rabbit, Topology.OPINIONS_QUEUE + ".dlq",
                m -> new String(m.getBody(), StandardCharsets.UTF_8).contains(id.toString()), WAIT);
        assertThat(dead.getMessageProperties().<String>getHeader("x-dead-letter-reason")).contains("unreadable");
    }
}
