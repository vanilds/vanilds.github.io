package io.github.vanilds.creditdesk.shared.security;

import io.github.vanilds.creditdesk.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityIT extends IntegrationTest {

    @Test
    void apiWithoutTokenIsProblem401WithBearerChallenge() throws Exception {
        mvc.perform(get("/api/customers").with(anonymous()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/customers"));
    }

    @Test
    void invalidTokenIsRefused() throws Exception {
        // A real decoder is used here (no jwt() shortcut): garbage must not pass signature verification
        mvc.perform(get("/api/customers").with(anonymous()).header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("invalid_token")));
    }

    @Test
    void probesAndContractArePublic() throws Exception {
        mvc.perform(get("/actuator/health/readiness").with(anonymous())).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs").with(anonymous())).andExpect(status().isOk());
    }

    @Test
    void unknownPathsAreClosed() throws Exception {
        mvc.perform(get("/actuator/env").with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test
    void auditorReadsButCannotWrite() throws Exception {
        mvc.perform(get("/api/customers").with(as(ALINE))).andExpect(status().isOk());
        mvc.perform(get("/api/proposals").with(as(ALINE))).andExpect(status().isOk());

        mvc.perform(post("/api/customers").with(as(ALINE)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "PF", "document", randomCpf(),
                                "name", "Not Allowed", "email", "x@example.com", "monthlyRevenue", "1"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/forbidden"));
    }

    @Test
    void tokenWithoutCreditDeskRoleCannotUseTheApi() throws Exception {
        mvc.perform(get("/api/customers").with(as(new TestUser("x", "nobody"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerInheritsAnalystPermissions() throws Exception {
        // MANAGER > ANALYST in the role hierarchy: a manager can register customers without holding ANALYST
        mvc.perform(post("/api/customers").with(as(MARCOS)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("type", "PF", "document", randomCpf(),
                                "name", "By Manager", "email", "m@example.com", "monthlyRevenue", "100"))))
                .andExpect(status().isCreated());
    }

    @Test
    void echoesOrGeneratesCorrelationId() throws Exception {
        mvc.perform(get("/api/customers").header("X-Correlation-Id", "abc-12345678"))
                .andExpect(header().string("X-Correlation-Id", "abc-12345678"));
        // unsafe values (log injection) are replaced, not echoed
        mvc.perform(get("/api/customers").header("X-Correlation-Id", "bad\nINFO fake log line"))
                .andExpect(header().string("X-Correlation-Id", org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }
}
