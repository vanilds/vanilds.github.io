package io.github.vanilds.creditdesk.shared.web;

import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * A business error that becomes an RFC 9457 problem. The {@code slug} builds the problem "type" URI, which
 * clients use to tell errors apart without parsing the human-readable message.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String slug;
    private final String title;

    public ApiException(HttpStatus status, String slug, String title, String detail) {
        super(detail);
        this.status = status;
        this.slug = slug;
        this.title = title;
    }

    public static ApiException notFound(String entity, UUID id) {
        return new ApiException(HttpStatus.NOT_FOUND, "not-found", "Resource not found",
                "%s %s was not found.".formatted(entity, id));
    }

    public static ApiException badRequest(String slug, String title, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, slug, title, detail);
    }

    public static ApiException conflict(String slug, String title, String detail) {
        return new ApiException(HttpStatus.CONFLICT, slug, title, detail);
    }

    /** 403 for a business rule about <i>who</i> may act (owner only, four-eyes), not about the role. */
    public static ApiException forbidden(String slug, String title, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, slug, title, detail);
    }

    /** 422: the request is well-formed but breaks a business rule in the current state. */
    public static ApiException unprocessable(String slug, String title, String detail) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, slug, title, detail);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }
}
