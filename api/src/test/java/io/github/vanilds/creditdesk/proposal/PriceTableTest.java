package io.github.vanilds.creditdesk.proposal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PriceTableTest {

    // Reference values computed independently with Python's decimal module (40 digits of precision)
    @ParameterizedTest(name = "{0} at {1}/month in {2}x = {3}")
    @CsvSource({
            "15000.00, 0.0199, 24, 792.17",
            "250000.00, 0.0149, 60, 6332.05",
            "18000.00, 0.0189, 36, 693.78",
            "1000.00, 0.02, 1, 1020.00",   // one installment: principal plus one month of interest
            "1000.00, 0, 3, 333.33"        // zero rate: simple division
    })
    void computesFixedInstallment(BigDecimal amount, BigDecimal rate, int installments, BigDecimal expected) {
        assertThat(PriceTable.installment(amount, rate, installments)).isEqualByComparingTo(expected);
    }

    @Test
    void totalIsInstallmentTimesCount() {
        assertThat(PriceTable.total(new BigDecimal("792.17"), 24)).isEqualByComparingTo("19012.08");
    }

    @Test
    void rejectsZeroInstallments() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PriceTable.installment(BigDecimal.TEN, BigDecimal.ONE, 0));
    }
}
