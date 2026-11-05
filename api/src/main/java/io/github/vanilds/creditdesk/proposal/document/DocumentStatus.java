package io.github.vanilds.creditdesk.proposal.document;

public enum DocumentStatus {
    /** The API signed an upload URL; the browser has not finished (or never started) sending the file. */
    PENDING_UPLOAD,
    /** The API checked the object in S3 (it exists, size and type match what was declared). */
    AVAILABLE
}
