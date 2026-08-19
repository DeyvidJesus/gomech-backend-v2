package com.gomech.api.modules.crm.domain;

import java.util.regex.Pattern;

/**
 * Utilitário de domínio para normalização e validação de documentos fiscais brasileiros (CPF e CNPJ).
 * Segue o cálculo oficial de dígitos verificadores (Módulo 11) sem dependência de bibliotecas externas.
 */
public final class CpfCnpjValidator {

    private static final Pattern NON_DIGITS = Pattern.compile("\\D");
    private static final int[] CNPJ_WEIGHTS_1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] CNPJ_WEIGHTS_2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CpfCnpjValidator() {
    }

    /**
     * Remove qualquer caractere não numérico da string informada.
     */
    public static String normalize(String document) {
        if (document == null) {
            return null;
        }
        String clean = NON_DIGITS.matcher(document).replaceAll("").trim();
        return clean.isEmpty() ? null : clean;
    }

    /**
     * Valida se a string normalizada ou formatada corresponde a um CPF (11 dígitos) ou CNPJ (14 dígitos) matematicamente válido.
     */
    public static boolean isValid(String document) {
        String clean = normalize(document);
        if (clean == null) {
            return false;
        }
        if (clean.length() == 11) {
            return isValidCpf(clean);
        }
        if (clean.length() == 14) {
            return isValidCnpj(clean);
        }
        return false;
    }

    /**
     * Validação de CPF com checagem de dígitos repetidos e cálculo dos dois dígitos verificadores.
     */
    public static boolean isValidCpf(String cpf) {
        String clean = normalize(cpf);
        if (clean == null || clean.length() != 11) {
            return false;
        }
        // Rejeita sequências conhecidas de dígitos repetidos
        if (clean.chars().distinct().count() == 1) {
            return false;
        }

        try {
            int sum1 = 0;
            for (int i = 0; i < 9; i++) {
                sum1 += (clean.charAt(i) - '0') * (10 - i);
            }
            int remainder1 = (sum1 * 10) % 11;
            int digit1 = (remainder1 == 10) ? 0 : remainder1;
            if (digit1 != (clean.charAt(9) - '0')) {
                return false;
            }

            int sum2 = 0;
            for (int i = 0; i < 10; i++) {
                sum2 += (clean.charAt(i) - '0') * (11 - i);
            }
            int remainder2 = (sum2 * 10) % 11;
            int digit2 = (remainder2 == 10) ? 0 : remainder2;
            return digit2 == (clean.charAt(10) - '0');
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validação de CNPJ com checagem de dígitos repetidos e pesos para os dígitos verificadores.
     */
    public static boolean isValidCnpj(String cnpj) {
        String clean = normalize(cnpj);
        if (clean == null || clean.length() != 14) {
            return false;
        }
        // Rejeita sequências conhecidas de dígitos repetidos
        if (clean.chars().distinct().count() == 1) {
            return false;
        }

        try {
            int sum1 = 0;
            for (int i = 0; i < 12; i++) {
                sum1 += (clean.charAt(i) - '0') * CNPJ_WEIGHTS_1[i];
            }
            int rem1 = sum1 % 11;
            int digit1 = (rem1 < 2) ? 0 : 11 - rem1;
            if (digit1 != (clean.charAt(12) - '0')) {
                return false;
            }

            int sum2 = 0;
            for (int i = 0; i < 13; i++) {
                sum2 += (clean.charAt(i) - '0') * CNPJ_WEIGHTS_2[i];
            }
            int rem2 = sum2 % 11;
            int digit2 = (rem2 < 2) ? 0 : 11 - rem2;
            return digit2 == (clean.charAt(13) - '0');
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Formata um CPF ou CNPJ normalizado com máscara.
     */
    public static String format(String document) {
        String clean = normalize(document);
        if (clean == null) {
            return null;
        }
        if (clean.length() == 11) {
            return clean.replaceFirst("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        }
        if (clean.length() == 14) {
            return clean.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
        }
        return clean;
    }
}
