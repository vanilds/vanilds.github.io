package io.github.vanilds.creditdesk.proposal.document;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.vanilds.creditdesk.IntegrationTest;
import io.github.vanilds.creditdesk.messaging.MessagingTestSupport;
import io.github.vanilds.creditdesk.messaging.Topology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Upload and download go to a real S3-compatible server (SeaweedFS), exactly as the browser would. */
class DocumentIT extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\n% CreditDesk test document\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Autowired
    RabbitTemplate rabbit;

    private UUID draftByAna() throws Exception {
        UUID customerId = createCustomer(unique("Documents"));
        return UUID.fromString(read(postJson("/api/proposals", Map.of("customerId", customerId, "kind", "NEW_CREDIT",
                "amount", "8000.00", "installments", 12, "monthlyRate", "0.0199"))).get("id").asText());
    }

    private JsonNode startUpload(UUID proposalId, String fileName, String type, long size) throws Exception {
        return read(postJson("/api/proposals/" + proposalId + "/documents", Map.of("category", "INCOME_PROOF",
                "fileName", fileName, "contentType", type, "sizeBytes", size)).andExpect(status().isCreated()));
    }

    /** What the Angular app does: PUT the bytes to the presigned URL with the headers the API returned. */
    private HttpResponse<String> putFile(JsonNode upload, byte[] bytes, String contentType) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(upload.get("url").asText()))
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void uploadConfirmListAndDownload() throws Exception {
        UUID proposalId = draftByAna();
        JsonNode started = startUpload(proposalId, "C:\\Users\\ana\\holerite outubro.pdf", "application/pdf", PDF.length);
        String documentId = started.at("/document/id").asText();
        assertThat(started.at("/document/status").asText()).isEqualTo("PENDING_UPLOAD");
        assertThat(started.at("/document/fileName").asText()).isEqualTo("holerite outubro.pdf");
        assertThat(started.at("/upload/method").asText()).isEqualTo("PUT");
        // The key in the URL is made of ids only, never the file name
        assertThat(started.at("/upload/url").asText()).contains("proposals/" + proposalId + "/" + documentId)
                .doesNotContain("holerite");

        // Not uploaded yet: nothing to confirm
        mvc.perform(post("/api/proposals/{id}/documents/{doc}/confirm", proposalId, documentId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/upload-not-found"));
        mvc.perform(get("/api/proposals/{id}/documents", proposalId)).andExpect(jsonPath("$", hasSize(0)));

        assertThat(putFile(started.get("upload"), PDF, "application/pdf").statusCode()).isEqualTo(200);
        mvc.perform(post("/api/proposals/{id}/documents/{doc}/confirm", proposalId, documentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        mvc.perform(post("/api/proposals/{id}/documents/{doc}/confirm", proposalId, documentId))
                .andExpect(status().isOk()); // a retried confirm is harmless

        mvc.perform(get("/api/proposals/{id}/documents", proposalId).with(as(ALINE)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category").value("INCOME_PROOF"))
                .andExpect(jsonPath("$[0].uploadedByName").value("ana"));

        JsonNode link = read(mvc.perform(post("/api/proposals/{id}/documents/{doc}/download", proposalId, documentId)
                .with(as(ALINE))).andExpect(status().isOk()));
        HttpResponse<byte[]> download = http.send(HttpRequest.newBuilder(URI.create(link.get("url").asText())).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(download.statusCode()).isEqualTo(200);
        assertThat(download.body()).isEqualTo(PDF);
        assertThat(download.headers().firstValue("Content-Disposition")).hasValueSatisfying(value ->
                assertThat(value).contains("attachment").contains("holerite%20outubro.pdf"));

        // Both the upload and the auditor's download are in the audit trail
        mvc.perform(get("/api/audit").with(as(ALINE)).param("entityId", proposalId.toString()))
                .andExpect(jsonPath("$.content[?(@.action == 'PROPOSAL_DOCUMENT_ADDED')].actorName").value("ana"))
                .andExpect(jsonPath("$.content[?(@.action == 'PROPOSAL_DOCUMENT_DOWNLOADED')].actorName").value("aline"));
    }

    @Test
    void s3RefusesAFileOtherThanTheOneTheApiSigned() throws Exception {
        UUID proposalId = draftByAna();
        JsonNode started = startUpload(proposalId, "statement.pdf", "application/pdf", PDF.length);

        // Same URL, other type or other size: the signature does not match, S3 says 403
        assertThat(putFile(started.get("upload"), PDF, "image/png").statusCode()).isEqualTo(403);
        assertThat(putFile(started.get("upload"), new byte[PDF.length + 10], "application/pdf").statusCode())
                .isEqualTo(403);
    }

    @Test
    void submittedEventListsTheAttachedCategories() throws Exception {
        UUID proposalId = draftByAna();
        JsonNode started = startUpload(proposalId, "payslip.pdf", "application/pdf", PDF.length);
        putFile(started.get("upload"), PDF, "application/pdf");
        mvc.perform(post("/api/proposals/{id}/documents/{doc}/confirm", proposalId, started.at("/document/id").asText()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/proposals/{id}/submit", proposalId).header("If-Match", version(0)))
                .andExpect(status().isOk());

        var message = MessagingTestSupport.receiveMatching(rabbit, Topology.WORKER_QUEUE,
                m -> new String(m.getBody(), StandardCharsets.UTF_8).contains(proposalId.toString()), Duration.ofSeconds(15));
        assertThat(MessagingTestSupport.body(json, message).get("documents").toString()).isEqualTo("[\"INCOME_PROOF\"]");
    }

    @Test
    void enforcesTheRules() throws Exception {
        UUID proposalId = draftByAna();
        String path = "/api/proposals/" + proposalId + "/documents";

        postJson(path, Map.of("category", "OTHER", "fileName", "page.html", "contentType", "text/html", "sizeBytes", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/unsupported-file-type"));
        postJson(path, Map.of("category", "OTHER", "fileName", "big.pdf", "contentType", "application/pdf",
                "sizeBytes", 10L * 1024 * 1024 + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/file-too-large"));
        postJson(path, Map.of("category", "OTHER", "fileName", "../", "contentType", "application/pdf", "sizeBytes", 10))
                .andExpect(status().isBadRequest());

        // Only the owner, only while DRAFT
        mvc.perform(post(path).with(as(BRUNO)).contentType("application/json").content("""
                        {"category": "OTHER", "fileName": "a.pdf", "contentType": "application/pdf", "sizeBytes": 10}"""))
                .andExpect(status().isForbidden());
        mvc.perform(post(path).with(as(ALINE)).contentType("application/json").content("""
                        {"category": "OTHER", "fileName": "a.pdf", "contentType": "application/pdf", "sizeBytes": 10}"""))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/proposals/{id}/submit", proposalId).header("If-Match", version(0)));
        postJson(path, Map.of("category", "OTHER", "fileName", "late.pdf", "contentType", "application/pdf", "sizeBytes", 10))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unknownDocumentIs404() throws Exception {
        UUID proposalId = draftByAna();
        mvc.perform(post("/api/proposals/{id}/documents/{doc}/download", proposalId, UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
