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

@ValidDocument
public record CreateCustomerRequest(
        @NotNull
        CustomerType type,

        @Schema(description = "CPF (PF) or CNPJ (PJ), with or without punctuation. Alphanumeric CNPJ is accepted.",
                example = "529.982.247-25")
        @NotBlank
        @Size(max = 20)
        String document,

        @Schema(example = "Maria da Silva")
        @NotBlank
        @Size(min = 3, max = 150)
        String name,

        @Schema(example = "maria.silva@example.com")
        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Schema(description = "Monthly income (PF) or revenue (PJ) in BRL", example = "8500.00")
        @NotNull
        @DecimalMin("0.00")
        @DecimalMax("9999999999999.99")
        @Digits(integer = 13, fraction = 2)
        BigDecimal monthlyRevenue
) {
}
