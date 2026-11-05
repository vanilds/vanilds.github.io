package io.github.vanilds.creditdesk.shared.validation;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * CPF and CNPJ normalization, check-digit validation, formatting and masking.
 *
 * <p>Supports the alphanumeric CNPJ introduced by Receita Federal (IN RFB 2.229/2024, issued from July 2026):
 * the first 12 characters may be letters A–Z, the last 2 (check digits) are always numeric. Each character's
 * value is its ASCII code minus 48, so digits keep their usual value and 'A' = 17 … 'Z' = 42. The modulo-11
 * algorithm and the weights are unchanged, which keeps all numeric CNPJs valid.
 */
public final class BrazilianDocuments {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s./-]");
    private static final Pattern CPF = Pattern.compile("\\d{11}");
    private static final Pattern CNPJ = Pattern.compile("[0-9A-Z]{12}\\d{2}");

    private static final int[] CNPJ_WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private BrazilianDocuments() {
    }

    /** Removes dots, slashes, dashes and spaces and upper-cases letters: "12.ABC.345/01DE-35" → "12ABC34501DE35". */
    public static String normalize(String raw) {
        return raw == null ? null : SEPARATORS.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
    }

    public static boolean isValidCpf(String raw) {
        String cpf = normalize(raw);
        if (cpf == null || !CPF.matcher(cpf).matches() || allSameChar(cpf)) {
            return false;
        }
        return cpf.charAt(9) == cpfCheckDigit(cpf, 9) && cpf.charAt(10) == cpfCheckDigit(cpf, 10);
    }

    public static boolean isValidCnpj(String raw) {
        String cnpj = normalize(raw);
        if (cnpj == null || !CNPJ.matcher(cnpj).matches() || allSameChar(cnpj)) {
            return false;
        }
        return cnpj.charAt(12) == cnpjCheckDigit(cnpj, CNPJ_WEIGHTS_1)
                && cnpj.charAt(13) == cnpjCheckDigit(cnpj, CNPJ_WEIGHTS_2);
    }

    /** "12345678909" → "123.456.789-09". Expects a normalized, valid CPF. */
    public static String formatCpf(String cpf) {
        return "%s.%s.%s-%s".formatted(cpf.substring(0, 3), cpf.substring(3, 6), cpf.substring(6, 9), cpf.substring(9));
    }

    /** "11222333000181" → "11.222.333/0001-81". Expects a normalized, valid CNPJ. */
    public static String formatCnpj(String cnpj) {
        return "%s.%s.%s/%s-%s".formatted(cnpj.substring(0, 2), cnpj.substring(2, 5), cnpj.substring(5, 8),
                cnpj.substring(8, 12), cnpj.substring(12));
    }

    /**
     * Masks a CPF keeping the 6 middle digits — the pattern recommended for publishing CPFs under LGPD:
     * "12345678909" → "***.456.789-**".
     */
    public static String maskCpf(String cpf) {
        return "***.%s.%s-**".formatted(cpf.substring(3, 6), cpf.substring(6, 9));
    }

    // CPF: weights run from (length + 1) down to 2; remainder < 2 gives digit 0, otherwise 11 - remainder.
    private static char cpfCheckDigit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        }
        return toCheckDigit(sum);
    }

    private static char cnpjCheckDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i]; // ASCII - 48: works for digits and letters
        }
        return toCheckDigit(sum);
    }

    private static char toCheckDigit(int sum) {
        int remainder = sum % 11;
        return (char) ('0' + (remainder < 2 ? 0 : 11 - remainder));
    }

    // 000.000.000-00, 111.111.111-11 … pass the modulo-11 check but are not real documents.
    private static boolean allSameChar(String value) {
        return value.chars().allMatch(c -> c == value.charAt(0));
    }
}
