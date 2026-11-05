package io.github.vanilds.creditdesk.audit;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChangesTest {

    @Test
    void keepsOnlyRealChanges() {
        var changes = new Changes()
                .field("name", "Ana", "Ana Maria")
                .field("email", "a@example.com", "a@example.com")
                .field("revenue", new BigDecimal("100.0"), new BigDecimal("100.00"));
        assertThat(changes.asMap()).containsOnlyKeys("name")
                .containsEntry("name", Map.of("from", "Ana", "to", "Ana Maria"));
    }

    @Test
    void masksEmailKeepingTheDomain() {
        assertThat(Masking.email("joana.prado@example.com")).isEqualTo("j**********@example.com");
        assertThat(Masking.email("ab@x.com")).isEqualTo("a***@x.com");
        assertThat(Masking.email("broken")).isEqualTo("***");
    }
}
