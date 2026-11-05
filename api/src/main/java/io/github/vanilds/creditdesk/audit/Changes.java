package io.github.vanilds.creditdesk.audit;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the "what changed" part of an audit record: only fields whose value really changed, in a stable order.
 *
 * <pre>new Changes().field("name", old, new).masked("email", Masking.email(old), Masking.email(new)).asMap()</pre>
 *
 * Personal data goes in already masked: the audit log is read by more people than the customer table, so it must
 * never become the place where the full CPF or e-mail can be found.
 */
public final class Changes {

    private final Map<String, Object> fields = new LinkedHashMap<>();

    /** Records {@code from → to} if the values differ. BigDecimals compare by value (100.0 equals 100.00). */
    public Changes field(String name, Object from, Object to) {
        if (!same(from, to)) {
            var change = new LinkedHashMap<String, Object>();
            change.put("from", from);
            change.put("to", to);
            fields.put(name, change);
        }
        return this;
    }

    /** Same as {@link #field}, for a value that must only ever appear masked. */
    public Changes masked(String name, String maskedFrom, String maskedTo) {
        return field(name, maskedFrom, maskedTo);
    }

    /** A value at creation time (no "from"). */
    public Changes created(String name, Object value) {
        return field(name, null, value);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(fields);
    }

    private static boolean same(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        return Objects.equals(a, b);
    }
}
