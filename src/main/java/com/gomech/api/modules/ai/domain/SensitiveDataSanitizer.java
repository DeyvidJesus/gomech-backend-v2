package com.gomech.api.modules.ai.domain;

import java.util.regex.Pattern;

public final class SensitiveDataSanitizer {

    private static final Pattern CPF_PATTERN = Pattern.compile(
            "\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b"
    );

    private static final Pattern CNPJ_PATTERN = Pattern.compile(
            "\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b"
    );

    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|3(?:0[0-5]|[68][0-9])[0-9]{11}|6(?:011|5[0-9]{2})[0-9]{12}|(?:2131|1800|35\\d{3})\\d{11})\\b"
    );

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+?55\\s?)?(?:\\(?\\d{2}\\)?\\s?)?(?:9\\d{4}|\\d{4})[-.\\s]?\\d{4}\\b"
    );

    private static final Pattern AUTH_TOKEN_PATTERN = Pattern.compile(
            "\\b(?:Bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*|ak-[a-zA-Z0-9]{16,}|sk-[a-zA-Z0-9]{16,}|ghp_[a-zA-Z0-9]{20,})\\b",
            Pattern.CASE_INSENSITIVE
    );

    private SensitiveDataSanitizer() {}

    public static String sanitize(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = input;
        result = AUTH_TOKEN_PATTERN.matcher(result).replaceAll("[SECRET_REDACTED]");
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("[CARD_REDACTED]");
        result = CNPJ_PATTERN.matcher(result).replaceAll("[CNPJ_REDACTED]");
        result = CPF_PATTERN.matcher(result).replaceAll("[CPF_REDACTED]");
        result = EMAIL_PATTERN.matcher(result).replaceAll("[EMAIL_REDACTED]");
        result = PHONE_PATTERN.matcher(result).replaceAll("[PHONE_REDACTED]");

        return result;
    }

    public static String summarize(String text, int maxLength) {
        if (text == null) return "";
        String sanitized = sanitize(text);
        if (sanitized.length() <= maxLength) {
            return sanitized;
        }
        return sanitized.substring(0, maxLength - 3) + "...";
    }
}
