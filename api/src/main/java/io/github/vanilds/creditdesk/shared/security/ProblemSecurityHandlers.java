package io.github.vanilds.creditdesk.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.vanilds.creditdesk.shared.web.ProblemHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;

/**
 * 401 and 403 happen in the security filters, before any controller, so {@code @RestControllerAdvice} never
 * sees them. These handlers keep them in the same RFC 9457 format as every other error, and keep the standard
 * {@code WWW-Authenticate: Bearer error="invalid_token"} header that OAuth2 clients rely on.
 *
 * <p>The body never says <i>why</i> a token was refused (expired? wrong signature?): that detail helps an
 * attacker more than a legitimate client, and it is in the header for those who need it.
 */
public class ProblemSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper json;
    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final BearerTokenAccessDeniedHandler bearerDenied = new BearerTokenAccessDeniedHandler();

    public ProblemSecurityHandlers(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        bearerEntryPoint.commence(request, response, exception); // sets status 401 + WWW-Authenticate
        write(request, response, HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required",
                "Send a valid access token: Authorization: Bearer <token>.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        bearerDenied.handle(request, response, exception); // sets status 403 + WWW-Authenticate
        write(request, response, HttpStatus.FORBIDDEN, "forbidden", "Access denied",
                "Your role does not allow this operation.");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String slug,
                       String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ProblemHandler.TYPE_BASE + slug));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
