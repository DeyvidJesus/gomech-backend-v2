package com.gomech.api.modules.crm.domain;

import java.util.regex.Pattern;

/**
 * Utilitário de domínio para normalização e validação de placas veiculares brasileiras.
 * Suporta o formato tradicional (ABC-1234) e o formato padrão Mercosul (ABC1D23 / ABC12D3).
 */
public final class LicensePlateValidator {

    private static final Pattern CLEAN_PATTERN = Pattern.compile("[^a-zA-Z0-9]");
    private static final Pattern TRADITIONAL_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{4}$");
    private static final Pattern MERCOSUL_CAR_PATTERN = Pattern.compile("^[A-Z]{3}[0-9][A-Z][0-9]{2}$");
    private static final Pattern MERCOSUL_MOTO_PATTERN = Pattern.compile("^[A-Z]{3}[0-9]{2}[A-Z][0-9]$");

    private LicensePlateValidator() {
    }

    /**
     * Normaliza a placa removendo traços, espaços e convertendo para letras maiúsculas.
     */
    public static String normalize(String licensePlate) {
        if (licensePlate == null) {
            return null;
        }
        String clean = CLEAN_PATTERN.matcher(licensePlate).replaceAll("").toUpperCase().trim();
        return clean.isEmpty() ? null : clean;
    }

    /**
     * Valida se a placa informada obedece aos padrões brasileiros (Tradicional ou Mercosul).
     */
    public static boolean isValid(String licensePlate) {
        String clean = normalize(licensePlate);
        if (clean == null || clean.length() != 7) {
            return false;
        }
        return TRADITIONAL_PATTERN.matcher(clean).matches()
                || MERCOSUL_CAR_PATTERN.matcher(clean).matches()
                || MERCOSUL_MOTO_PATTERN.matcher(clean).matches();
    }

    /**
     * Formata a placa no padrão legível (ex: ABC-1234 para tradicional ou ABC1D23 para Mercosul).
     */
    public static String format(String licensePlate) {
        String clean = normalize(licensePlate);
        if (clean == null) {
            return null;
        }
        if (TRADITIONAL_PATTERN.matcher(clean).matches()) {
            return clean.substring(0, 3) + "-" + clean.substring(3);
        }
        return clean;
    }
}
