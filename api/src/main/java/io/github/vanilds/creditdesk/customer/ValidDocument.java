package io.github.vanilds.creditdesk.customer;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cross-field rule: the document must be a valid CPF when type is PF, and a valid CNPJ when type is PJ.
 * The error is reported on the "document" field so the form can show it next to the input.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDocument.Validator.class)
public @interface ValidDocument {

    String message() default "invalid document";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidDocument, CreateCustomerRequest> {

        @Override
        public boolean isValid(CreateCustomerRequest request, ConstraintValidatorContext context) {
            if (request.type() == null || request.document() == null || request.document().isBlank()) {
                return true; // @NotNull / @NotBlank already report these
            }
            if (request.type().isValidDocument(request.document())) {
                return true;
            }
            String message = request.type() == CustomerType.PF ? "invalid CPF" : "invalid CNPJ";
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(message)
                    .addPropertyNode("document")
                    .addConstraintViolation();
            return false;
        }
    }
}
