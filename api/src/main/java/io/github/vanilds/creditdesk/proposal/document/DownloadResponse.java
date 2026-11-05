package io.github.vanilds.creditdesk.proposal.document;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Open the URL to download; it expires in about a minute and works for this one file only.")
public record DownloadResponse(String url, Instant expiresAt) {
}
