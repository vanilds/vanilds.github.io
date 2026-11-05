package io.github.vanilds.creditdesk;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(title = "CreditDesk API", version = "0.1.0",
                description = "Credit and debt-renegotiation proposals for bank analysts. All data is synthetic."),
        // Relative server keeps the generated contract identical on every machine (no random test port in it)
        servers = @Server(url = "/", description = "Same origin"))
public class CreditDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditDeskApplication.class, args);
    }

    /** Injected instead of calling Instant.now() directly, so tests can freeze time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Business time zone: "today", and date filters like from=2026-11-01, are São Paulo dates. */
    @Bean
    ZoneId businessZone(@Value("${creditdesk.zone-id}") String zoneId) {
        return ZoneId.of(zoneId);
    }
}
