package io.github.vanilds.creditdesk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vanilds.creditdesk.shared.security.Role;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class: the whole application against a real PostgreSQL. All test classes share one Spring context
 * (and so one container), which keeps the suite fast. Tests avoid depending on each other by using
 * unique documents and names.
 *
 * <p>Security: every request is sent as the analyst {@link #ANA} unless a test says otherwise with
 * {@code .with(as(MARCOS))}. Tokens are built by spring-security-test, so tests never need a running Keycloak;
 * {@code KeycloakRolesConverterTest} covers the token-to-roles mapping that this shortcut skips.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability // tests switch metrics export off by default; /actuator/prometheus is tested
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TestSecurityConfiguration.class})
public abstract class IntegrationTest {

    /** A user as Keycloak would describe it: a stable id (token "sub"), a username and realm roles. */
    public record TestUser(String id, String username, Role... roles) {
    }

    public static final TestUser ANA = new TestUser("11111111-0000-4000-8000-000000000001", "ana", Role.ANALYST);
    public static final TestUser BRUNO = new TestUser("11111111-0000-4000-8000-000000000002", "bruno", Role.ANALYST);
    public static final TestUser MARCOS = new TestUser("11111111-0000-4000-8000-000000000003", "marcos", Role.MANAGER);
    public static final TestUser PAULA = new TestUser("11111111-0000-4000-8000-000000000004", "paula", Role.MANAGER);
    public static final TestUser ALINE = new TestUser("11111111-0000-4000-8000-000000000005", "aline", Role.AUDITOR);

    /** Authenticates the request as {@code user}, with a JWT shaped like Keycloak's. */
    public static RequestPostProcessor as(TestUser user) {
        return jwt()
                .jwt(token -> token.subject(user.id()).claim("preferred_username", user.username()))
                .authorities(Arrays.stream(user.roles())
                        .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role.authority())).toList());
    }

    /** "If-Match" value for a version. */
    protected static String version(long version) {
        return "\"" + version + "\"";
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    protected ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    protected JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    /** Creates a PF customer and returns its id. */
    protected UUID createCustomer(String name) throws Exception {
        var result = postJson("/api/customers", Map.of(
                "type", "PF", "document", randomCpf(), "name", name,
                "email", "test@example.com", "monthlyRevenue", "5000.00"))
                .andExpect(status().isCreated());
        return UUID.fromString(read(result).get("id").asText());
    }

    /** A random CPF with valid check digits, so tests never collide on the unique index. */
    protected static String randomCpf() {
        var random = ThreadLocalRandom.current();
        int[] digits = new int[11];
        do {
            for (int i = 0; i < 9; i++) {
                digits[i] = random.nextInt(10);
            }
        } while (digits[0] == digits[1] && digits[1] == digits[2]);
        for (int length = 9; length <= 10; length++) {
            int sum = 0;
            for (int i = 0; i < length; i++) {
                sum += digits[i] * (length + 1 - i);
            }
            int remainder = sum % 11;
            digits[length] = remainder < 2 ? 0 : 11 - remainder;
        }
        var cpf = new StringBuilder();
        for (int digit : digits) {
            cpf.append(digit);
        }
        return cpf.toString();
    }

    protected static String unique(String prefix) {
        return prefix + " " + UUID.randomUUID().toString().substring(0, 8);
    }
}
