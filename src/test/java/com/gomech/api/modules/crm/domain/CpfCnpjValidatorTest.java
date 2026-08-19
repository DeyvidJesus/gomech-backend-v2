package com.gomech.api.modules.crm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CpfCnpjValidatorTest {

    @Test
    @DisplayName("Deve normalizar CPF e CNPJ removendo pontuações")
    void shouldNormalizeDocuments() {
        assertThat(CpfCnpjValidator.normalize("123.456.789-00")).isEqualTo("12345678900");
        assertThat(CpfCnpjValidator.normalize("12.345.678/0001-95")).isEqualTo("12345678000195");
        assertThat(CpfCnpjValidator.normalize("   ")).isNull();
        assertThat(CpfCnpjValidator.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "52998224725", // CPF válido
            "11144477735", // CPF válido
            "12345678909", // CPF válido
            "00000000191", // CPF válido
            "04.252.011/0001-10", // CNPJ válido formatado
            "11.222.333/0001-81", // CNPJ válido formatado
            "04252011000110", // CNPJ válido sem formato
            "11222333000181"  // CNPJ válido sem formato
    })
    @DisplayName("Deve validar documentos válidos (CPF e CNPJ)")
    void shouldValidateValidDocuments(String document) {
        assertThat(CpfCnpjValidator.isValid(document)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00000000000", // CPF repetido
            "11111111111", // CPF repetido
            "12345678900", // CPF com dígito inválido
            "12345",       // Comprimento inválido
            "00000000000000", // CNPJ repetido
            "11111111111111", // CNPJ repetido
            "11222333000100", // CNPJ com dígito inválido
            "abc",
            ""
    })
    @DisplayName("Deve rejeitar documentos inválidos ou com dígitos verificadores incorretos")
    void shouldRejectInvalidDocuments(String document) {
        assertThat(CpfCnpjValidator.isValid(document)).isFalse();
    }

    @Test
    @DisplayName("Deve formatar CPF e CNPJ com máscara adequada")
    void shouldFormatCpfAndCnpj() {
        assertThat(CpfCnpjValidator.format("52998224725")).isEqualTo("529.982.247-25");
        assertThat(CpfCnpjValidator.format("04252011000110")).isEqualTo("04.252.011/0001-10");
        assertThat(CpfCnpjValidator.format(null)).isNull();
    }
}
