package io.github.vanilds.creditdesk.shared.web;

import io.github.vanilds.creditdesk.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Two people editing the same record: the second save must fail loudly, never silently overwrite. */
class OptimisticLockingIT extends IntegrationTest {

    private static final String EDIT = """
            {"name": "%s", "email": "edit@example.com", "monthlyRevenue": 1000.00}
            """;

    @Test
    void readReturnsVersionAsETag() throws Exception {
        UUID id = createCustomer(unique("ETag"));
        mvc.perform(get("/api/customers/{id}", id)).andExpect(header().string("ETag", "\"0\""));
    }

    @Test
    void writeWithoutIfMatchIs428() throws Exception {
        UUID id = createCustomer(unique("No If-Match"));
        mvc.perform(put("/api/customers/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(EDIT.formatted("Whoever")))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/precondition-required"));
    }

    @Test
    void secondEditorWithStaleVersionGets412() throws Exception {
        UUID id = createCustomer(unique("Lost update"));
        // both read version 0
        mvc.perform(put("/api/customers/{id}", id).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT.formatted("First editor")))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""));

        mvc.perform(put("/api/customers/{id}", id).header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT.formatted("Second editor")))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.type").value("https://vanilds.github.io/problems/precondition-failed"));

        mvc.perform(get("/api/customers/{id}", id)).andExpect(jsonPath("$.name").value("First editor"));
    }

    @Test
    void weakETagFormIsAccepted() throws Exception {
        UUID id = createCustomer(unique("Weak"));
        mvc.perform(put("/api/customers/{id}", id).header("If-Match", "W/\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(EDIT.formatted("Via proxy")))
                .andExpect(status().isOk());
    }
}
