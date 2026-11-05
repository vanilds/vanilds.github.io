package io.github.vanilds.creditdesk.shared.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optimistic locking over HTTP (RFC 9110 §13): the entity's JPA {@code @Version} travels as the ETag.
 *
 * <pre>
 * GET  /api/proposals/42            → 200, ETag: "3"
 * PUT  /api/proposals/42  If-Match: "3"  → 200, ETag: "4"
 * PUT  /api/proposals/42  If-Match: "3"  → 412 (someone else already saved version 4)
 * PUT  /api/proposals/42  (no If-Match)  → 428 (the client must prove which version it saw)
 * </pre>
 *
 * Without this, two analysts editing the same proposal would both "win": the last save silently erases the
 * first one ("lost update"). Requiring If-Match turns that into an explicit, recoverable error.
 *
 * <p>Why not Spring's {@code ShallowEtagHeaderFilter}? It hashes the response body after the fact: good for
 * caching GETs, useless for writes, because the server would have to render the old body to compare.
 * The version column is cheaper and exact. The JPA {@code @Version} check still runs at commit time and catches
 * the tiny window between this comparison and the UPDATE.
 */
public final class ETags {

    private static final Pattern VERSION = Pattern.compile("^(?:W/)?\"(\\d{1,18})\"$");

    private ETags() {
    }

    public static String of(long version) {
        return "\"" + version + "\"";
    }

    public static <T> ResponseEntity<T> ok(T body, long version) {
        return ResponseEntity.ok().eTag(of(version)).body(body);
    }

    /** Throws 428 if the header is missing, 412 if it does not match the current version. */
    public static void require(String ifMatch, long currentVersion) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "precondition-required", "If-Match required",
                    "Send If-Match with the ETag you received when reading this record, e.g. If-Match: \"%d\"."
                            .formatted(currentVersion));
        }
        Matcher matcher = VERSION.matcher(ifMatch.trim());
        if (!matcher.matches() || Long.parseLong(matcher.group(1)) != currentVersion) {
            throw stale();
        }
    }

    public static ApiException stale() {
        return new ApiException(HttpStatus.PRECONDITION_FAILED, "precondition-failed", "Stale version",
                "This record was changed by someone else. Reload it and try again.");
    }
}
