package com.gomech.api.modules.ai.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataSanitizerTest {

    @Test
    @DisplayName("Deve sanitizar CPF em múltiplos formatos")
    void shouldSanitizeCpf() {
        String input = "O cliente com CPF 123.456.789-00 e outro com 98765432100 relataram ruído.";
        String result = SensitiveDataSanitizer.sanitize(input);

        assertFalse(result.contains("123.456.789-00"));
        assertFalse(result.contains("98765432100"));
        assertTrue(result.contains("[CPF_REDACTED]"));
    }

    @Test
    @DisplayName("Deve sanitizar CNPJ")
    void shouldSanitizeCnpj() {
        String input = "Faturar para a frota CNPJ 12.345.678/0001-99 urgente.";
        String result = SensitiveDataSanitizer.sanitize(input);

        assertFalse(result.contains("12.345.678/0001-99"));
        assertTrue(result.contains("[CNPJ_REDACTED]"));
    }

    @Test
    @DisplayName("Deve sanitizar e-mails e telefones")
    void shouldSanitizeEmailAndPhone() {
        String input = "Contato do motorista: joao.silva@empresa.com.br ou (11) 98765-4321.";
        String result = SensitiveDataSanitizer.sanitize(input);

        assertFalse(result.contains("joao.silva@empresa.com.br"));
        assertFalse(result.contains("98765-4321"));
        assertTrue(result.contains("[EMAIL_REDACTED]"));
        assertTrue(result.contains("[PHONE_REDACTED]"));
    }

    @Test
    @DisplayName("Deve sanitizar tokens de autorização e API keys")
    void shouldSanitizeAuthTokens() {
        String input = "Header: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.e30.t-ID and secret sk-1234567890abcdef1234567890.";
        String result = SensitiveDataSanitizer.sanitize(input);

        assertFalse(result.contains("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"));
        assertFalse(result.contains("sk-1234567890abcdef1234567890"));
        assertTrue(result.contains("[SECRET_REDACTED]"));
    }
}
