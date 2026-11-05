package io.github.vanilds.creditdesk.proposal;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Tabela Price (French amortization): fixed installments, the system used by most consumer loans in Brazil.
 *
 * <pre>installment = amount × i / (1 − (1 + i)^−n)</pre>
 *
 * where i is the monthly rate and n the number of installments. With i = 0 it is simply amount / n.
 * Money is always BigDecimal: double cannot represent 0.10 exactly, and cents matter in a bank.
 */
public final class PriceTable {

    private static final MathContext PRECISION = MathContext.DECIMAL128; // 34 digits, more than enough
    private static final int CENTS = 2;

    private PriceTable() {
    }

    public static BigDecimal installment(BigDecimal amount, BigDecimal monthlyRate, int installments) {
        if (installments < 1) {
            throw new IllegalArgumentException("installments must be at least 1");
        }
        if (monthlyRate.signum() == 0) {
            return amount.divide(BigDecimal.valueOf(installments), CENTS, RoundingMode.HALF_UP);
        }
        // (1 + i)^-n computed as 1 / (1 + i)^n
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(installments, PRECISION);
        BigDecimal discount = BigDecimal.ONE.divide(growth, PRECISION);
        BigDecimal factor = monthlyRate.divide(BigDecimal.ONE.subtract(discount), PRECISION);
        return amount.multiply(factor, PRECISION).setScale(CENTS, RoundingMode.HALF_UP);
    }

    /** What the customer pays in total over the whole plan. */
    public static BigDecimal total(BigDecimal installment, int installments) {
        return installment.multiply(BigDecimal.valueOf(installments)).setScale(CENTS, RoundingMode.HALF_UP);
    }
}
