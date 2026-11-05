package io.github.vanilds.creditdesk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

@SpringBootApplication
@EnableScheduling // OutboxRelay
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
