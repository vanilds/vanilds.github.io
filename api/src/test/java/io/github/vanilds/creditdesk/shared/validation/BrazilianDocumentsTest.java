package io.github.vanilds.creditdesk.shared.validation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BrazilianDocumentsTest {

    @Test
    void normalizeRemovesPunctuationAndUppercases() {
        assertThat(BrazilianDocuments.normalize(" 12.abc.345/01de-35 ")).isEqualTo("12ABC34501DE35");
        assertThat(BrazilianDocuments.normalize("529.982.247-25")).isEqualTo("52998224725");
    }

    @Nested
    class Cpf {

        @ParameterizedTest
        @ValueSource(strings = {"529.982.247-25", "52998224725", "111.444.777-35", "10433218100"})
        void acceptsValidCpf(String cpf) {
            assertThat(BrazilianDocuments.isValidCpf(cpf)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "529.982.247-24",  // wrong second check digit
                "529.982.247-15",  // wrong first check digit
                "111.111.111-11",  // passes modulo 11 but all digits equal
                "000.000.000-00",
                "5299822472",      // 10 digits
                "529982247250",    // 12 digits
                "52998224A25"      // letters are not allowed in CPF
        })
        void rejectsInvalidCpf(String cpf) {
            assertThat(BrazilianDocuments.isValidCpf(cpf)).isFalse();
        }

        @ParameterizedTest
        @NullAndEmptySource
        void rejectsMissingCpf(String cpf) {
            assertThat(BrazilianDocuments.isValidCpf(cpf)).isFalse();
        }

        @Test
        void formatsAndMasks() {
            assertThat(BrazilianDocuments.formatCpf("52998224725")).isEqualTo("529.982.247-25");
            assertThat(BrazilianDocuments.maskCpf("52998224725")).isEqualTo("***.982.247-**");
        }
    }

    @Nested
    class Cnpj {

        @ParameterizedTest
        @ValueSource(strings = {
                "11.222.333/0001-81",
                "11222333000181",
                "12.ABC.345/01DE-35",  // Receita Federal's own example of the alphanumeric CNPJ
                "12.abc.345/01de-35"   // lower case is normalized
        })
        void acceptsValidCnpj(String cnpj) {
            assertThat(BrazilianDocuments.isValidCnpj(cnpj)).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "11.222.333/0001-82",  // wrong check digit
                "12.ABC.345/01DE-36",  // wrong check digit, alphanumeric
                "12.ABC.345/01DE-3A",  // check digits are always numeric
                "00.000.000/0000-00",
                "1122233300018",       // 13 characters
                "12.AB@.345/01DE-35"   // invalid character
        })
        void rejectsInvalidCnpj(String cnpj) {
            assertThat(BrazilianDocuments.isValidCnpj(cnpj)).isFalse();
        }

        @Test
        void formats() {
            assertThat(BrazilianDocuments.formatCnpj("11222333000181")).isEqualTo("11.222.333/0001-81");
            assertThat(BrazilianDocuments.formatCnpj("12ABC34501DE35")).isEqualTo("12.ABC.345/01DE-35");
        }
    }
}
