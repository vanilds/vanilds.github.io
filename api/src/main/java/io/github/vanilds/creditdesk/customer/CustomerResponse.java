package io.github.vanilds.creditdesk.customer;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        CustomerType type,
        @Schema(description = "CPF masked (LGPD) or CNPJ formatted", example = "***.982.247-**")
        String document,
        String name,
        String email,
        BigDecimal monthlyRevenue,
        Instant createdAt,
        Instant updatedAt,
        long version
) {

    static CustomerResponse from(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getType(), customer.displayDocument(),
                customer.getName(), customer.getEmail(), customer.getMonthlyRevenue(), customer.getCreatedAt(),
                customer.getUpdatedAt(), customer.getVersion());
    }
}
