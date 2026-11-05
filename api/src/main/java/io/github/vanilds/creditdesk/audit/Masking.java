package io.github.vanilds.creditdesk.audit;

/** Masks for personal data that is written to the audit log (LGPD: data minimization). */
public final class Masking {

    private Masking() {
    }

    /** "maria.silva@example.com" → "m*********@example.com". Keeps the domain, which is rarely personal. */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "*".repeat(Math.max(3, at - 1)) + email.substring(at);
    }
}
