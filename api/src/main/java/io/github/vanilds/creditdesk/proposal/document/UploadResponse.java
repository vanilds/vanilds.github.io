package io.github.vanilds.creditdesk.proposal.document;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Send the file with exactly this method, URL and headers, then call confirm.")
public record UploadResponse(DocumentResponse document, DocumentStorage.SignedRequest upload) {
}
