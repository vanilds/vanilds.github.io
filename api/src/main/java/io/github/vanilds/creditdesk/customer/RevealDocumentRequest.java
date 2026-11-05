package io.github.vanilds.creditdesk.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** LGPD: access to the full CPF must have a purpose, and the purpose is recorded in the audit log. */
public record RevealDocumentRequest(
        @Schema(description = "Why the full document is needed", example = "Ticket SAC-4821: confirm customer identity")
        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
