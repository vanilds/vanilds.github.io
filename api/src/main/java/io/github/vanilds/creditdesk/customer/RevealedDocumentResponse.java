package io.github.vanilds.creditdesk.customer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RevealedDocumentResponse(
        UUID customerId,
        CustomerType type,
        @Schema(description = "Full CPF/CNPJ, formatted", example = "529.982.247-25")
        String document
) {
}
