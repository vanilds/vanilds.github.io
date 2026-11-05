package io.github.vanilds.creditdesk;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationsIT extends IntegrationTest {

    /** The endpoints Kubernetes calls (k8s/base/api.yaml). */
    @Test
    void exposesHealthProbes() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void exposesPrometheusMetrics() throws Exception {
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk());
    }

    /**
     * Writes the contract to api/openapi.yaml. The front-ends (Phase 3) generate their clients from that file.
     * CI fails if the committed file differs from what the code produces, so the contract never drifts silently.
     */
    @Test
    void exportsOpenApiContract() throws Exception {
        String yaml = mvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("/api/customers", "/api/proposals", "CreditDesk API");
        Files.writeString(Path.of("openapi.yaml"), yaml, StandardCharsets.UTF_8);
    }
}
