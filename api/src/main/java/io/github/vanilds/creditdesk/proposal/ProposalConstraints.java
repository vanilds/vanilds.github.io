package io.github.vanilds.creditdesk.proposal;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Composed constraints: each business limit is written once and reused by the create and update requests,
 * so the two cannot drift apart.
 */
public final class ProposalConstraints {

    private ProposalConstraints() {
    }

    /** Principal in BRL, 100.00 to 1,000,000.00, in cents. */
    @NotNull
    @DecimalMin("100.00")
    @DecimalMax("1000000.00")
    @Digits(integer = 13, fraction = 2)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface Amount {
        String message() default "";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** 1 to 120 monthly installments (10 years). */
    @NotNull
    @Min(1)
    @Max(120)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface Installments {
        String message() default "";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Monthly rate as a fraction (0.0199 = 1.99% a month), from 0 to 0.2, up to 6 decimal places. */
    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("0.2")
    @Digits(integer = 1, fraction = 6)
    @Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = {})
    public @interface MonthlyRate {
        String message() default "";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }
}
