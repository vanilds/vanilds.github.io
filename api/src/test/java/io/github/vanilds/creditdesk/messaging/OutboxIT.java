package io.github.vanilds.creditdesk.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vanilds.creditdesk.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OutboxIT extends IntegrationTest {

    @Autowired
    RabbitTemplate rabbit;

    @Test
    void submittingAProposalPublishesAMaskedEventForTheWorker() throws Exception {
        String name = unique("Outbox Customer");
        UUID customerId = createCustomer(name);
        UUID proposalId = UUID.fromString(read(postJson("/api/proposals", Map.of("customerId", customerId,
                "kind", "RENEGOTIATION", "amount", "12000.00", "installments", 24, "monthlyRate", "0.0199"))
                .andExpect(status().isCreated())).get("id").asText());

        mvc.perform(post("/api/proposals/{id}/submit", proposalId).header("If-Match", version(0))
                        .header("X-Correlation-Id", "outbox-test-" + proposalId.toString().substring(0, 8)))
                .andExpect(status().isOk());

        Message message = MessagingTestSupport.receiveMatching(rabbit, Topology.WORKER_QUEUE,
                m -> new String(m.getBody(), StandardCharsets.UTF_8).contains(proposalId.toString()),
                Duration.ofSeconds(15));

        var properties = message.getMessageProperties();
        assertThat(properties.getType()).isEqualTo("proposal.submitted");
        assertThat(properties.getContentType()).isEqualTo("application/json");
        assertThat(properties.<String>getHeader(Topology.CORRELATION_HEADER)).startsWith("outbox-test-");
        UUID eventId = UUID.fromString(properties.getMessageId());

        JsonNode event = MessagingTestSupport.body(json, message);
        assertThat(event.get("proposalVersion").asLong()).isEqualTo(1);
        assertThat(event.get("kind").asText()).isEqualTo("RENEGOTIATION");
        assertThat(event.get("installmentAmount").decimalValue()).isPositive();
        assertThat(event.at("/customer/type").asText()).isEqualTo("PF");
        assertThat(event.at("/customer/monthlyRevenue").decimalValue()).isEqualByComparingTo("5000.00");
        assertThat(event.get("documents")).isEmpty();
        // Data minimization: nothing that identifies the customer leaves the API
        String raw = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat(raw).doesNotContain(name, customerId.toString(), "test@example.com");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT published_at, attempts, event_type FROM outbox_event WHERE id = ?", eventId);
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("event_type")).isEqualTo("proposal.submitted");
    }

    @Test
    void aRolledBackSubmitLeavesNoEvent() throws Exception {
        UUID customerId = createCustomer(unique("Outbox Rollback"));
        UUID proposalId = UUID.fromString(read(postJson("/api/proposals", Map.of("customerId", customerId,
                "kind", "NEW_CREDIT", "amount", "5000.00", "installments", 12, "monthlyRate", "0.0199"))).get("id").asText());

        // Bruno is not the owner: the submit fails, so the transaction (and its outbox row) rolls back
        mvc.perform(post("/api/proposals/{id}/submit", proposalId).with(as(BRUNO)).header("If-Match", version(0)))
                .andExpect(status().isForbidden());

        Integer events = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?",
                Integer.class, proposalId);
        assertThat(events).isZero();
    }
}
