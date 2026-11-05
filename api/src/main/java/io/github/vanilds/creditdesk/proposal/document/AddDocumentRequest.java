package io.github.vanilds.creditdesk.proposal.document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddDocumentRequest(
        @NotNull DocumentCategory category,
        @Schema(example = "payslip-october.pdf") @NotBlank @Size(max = 200) String fileName,
        @Schema(example = "application/pdf", description = "application/pdf, image/jpeg or image/png")
        @NotBlank String contentType,
        @Schema(example = "184320", description = "Exact size in bytes, at most 10 MB. S3 refuses any other size.")
        @Positive long sizeBytes
) {
}
