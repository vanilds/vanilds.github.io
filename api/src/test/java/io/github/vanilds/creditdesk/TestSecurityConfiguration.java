package io.github.vanilds.creditdesk;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Default identity for MockMvc requests: the analyst ANA. A test's own {@code .with(as(...))} replaces it. */
@TestConfiguration(proxyBeanMethods = false)
public class TestSecurityConfiguration {

    @Bean
    MockMvcBuilderCustomizer defaultUser() {
        return builder -> builder.defaultRequest(get("/").with(IntegrationTest.as(IntegrationTest.ANA)));
    }
}
