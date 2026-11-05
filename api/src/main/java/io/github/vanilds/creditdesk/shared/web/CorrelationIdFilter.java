package io.github.vanilds.creditdesk.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * One id per request, from the browser to the audit log and, through a RabbitMQ message header, to the AI worker.
 * The client may send {@code X-Correlation-Id}; otherwise one is generated. It goes into every log line (MDC),
 * into the response header and into each audit record, so a support ticket with one id finds everything.
 *
 * <p>Runs before Spring Security, so even a 401 carries the id. The incoming value is validated because it ends
 * up in logs: an attacker must not be able to inject new log lines or huge strings through it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String id = incoming != null && SAFE.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, id);
        response.setHeader(HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY); // threads are reused by the server: never leak an id into the next request
        }
    }

    /** The current request's id, or null outside a request (e.g. a migration). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
