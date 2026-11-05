package io.github.vanilds.creditdesk.shared.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Turns every error into RFC 9457 {@code application/problem+json}:
 * <pre>
 * { "type": "https://vanilds.github.io/problems/validation", "title": "Invalid request", "status": 400,
 *   "detail": "...", "instance": "/api/customers", "errors": [ { "field": "document", "message": "..." } ] }
 * </pre>
 * Framework errors (404 route, 405, malformed JSON) go through the parent class, so they share the format.
 */
@RestControllerAdvice
public class ProblemHandler extends ResponseEntityExceptionHandler {

    public static final String TYPE_BASE = "https://vanilds.github.io/problems/";

    private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);

    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException ex) {
        return problem(ex.getStatus(), ex.getSlug(), ex.getTitle(), ex.getMessage());
    }

    /**
     * Two users passed the If-Match check at the same instant; JPA's version check at UPDATE time stops the second.
     * Same answer as a stale If-Match (412), because for the client it is the same situation.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ApiException stale = ETags.stale();
        return problem(stale.getStatus(), stale.getSlug(), stale.getTitle(), stale.getMessage());
    }

    /**
     * Last line of defence: the service checks duplicates first, but two simultaneous requests can both pass that
     * check. The unique index in the database is what really guarantees it.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrity(DataIntegrityViolationException ex) {
        String message = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (message.contains("customer_document_hash_active_uk")) {
            return problem(HttpStatus.CONFLICT, "duplicate-document", "Duplicate document",
                    "A customer with this document already exists.");
        }
        log.error("Data integrity violation", ex);
        return problem(HttpStatus.CONFLICT, "data-integrity", "Data integrity violation",
                "The request conflicts with existing data.");
    }

    /** Wrong type in a query parameter, e.g. {@code ?status=FOO} or {@code ?from=yesterday}. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation", "Invalid request",
                "Parameter '%s' has an invalid value.".formatted(ex.getName()));
        problem.setProperty("errors", List.of(new FieldViolation(ex.getName(), "invalid value: " + ex.getValue())));
        return problem;
    }

    /** Bean Validation on a request body (@Valid). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        List<FieldViolation> globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(error -> new FieldViolation(error.getObjectName(), error.getDefaultMessage()))
                .toList();
        return validationProblem(concat(errors, globalErrors));
    }

    /** Bean Validation on query/path parameters (@Min, @Max on @RequestParam). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldViolation> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                error instanceof FieldError fieldError
                                        ? fieldError.getField()
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validationProblem(errors);
    }

    private ResponseEntity<Object> validationProblem(List<FieldViolation> errors) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "validation", "Invalid request",
                "One or more fields are invalid.");
        problem.setProperties(Map.of("errors", errors));
        return ResponseEntity.badRequest().body(problem);
    }

    private static ProblemDetail problem(HttpStatus status, String slug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(TYPE_BASE + slug));
        problem.setTitle(title);
        return problem;
    }

    private static <T> List<T> concat(List<T> first, List<T> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }
}
