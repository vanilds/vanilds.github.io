package io.github.vanilds.creditdesk.customer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Type and document are not here on purpose: they identify the customer and cannot change. */
public record UpdateCustomerRequest(
        @Schema(example = "Maria da Silva Souza")
        @NotBlank
        @Size(min = 3, max = 150)
        String name,

        @Schema(example = "maria.souza@example.com")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Schema(example = "9200.00")
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("9999999999999.99")
        @Digits(integer = 13, fraction = 2)
        BigDecimal monthlyRevenue
) {
}
