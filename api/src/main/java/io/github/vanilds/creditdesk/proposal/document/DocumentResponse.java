package io.github.vanilds.creditdesk.proposal.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        DocumentCategory category,
        String fileName,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String uploadedByName,
        Instant createdAt,
        Instant confirmedAt
) {

    static DocumentResponse from(ProposalDocument document) {
        return new DocumentResponse(document.getId(), document.getCategory(), document.getFileName(),
                document.getContentType(), document.getSizeBytes(), document.getStatus(),
                document.getUploadedByName(), document.getCreatedAt(), document.getConfirmedAt());
    }
}
