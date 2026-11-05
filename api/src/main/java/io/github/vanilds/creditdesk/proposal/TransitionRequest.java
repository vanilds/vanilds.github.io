package io.github.vanilds.creditdesk.proposal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Optional body of the workflow endpoints. {@code reason} is mandatory for reject and return. */
public record TransitionRequest(
        @Schema(description = "Mandatory for reject and return; shown to the analyst", example = "Missing proof of income")
        @Size(max = 500)
        String reason
) {
}
