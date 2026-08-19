package com.gomech.api.modules.crm.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LicensePlateValidatorTest {

    @Test
    @DisplayName("Deve normalizar placas removendo traços e espaços e convertendo para maiúsculo")
    void shouldNormalizeLicensePlates() {
        assertThat(LicensePlateValidator.normalize("abc-1234")).isEqualTo("ABC1234");
        assertThat(LicensePlateValidator.normalize("  abc1d23 ")).isEqualTo("ABC1D23");
        assertThat(LicensePlateValidator.normalize("abc-1d23")).isEqualTo("ABC1D23");
        assertThat(LicensePlateValidator.normalize(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ABC-1234", // Tradicional com traço
            "ABC1234",  // Tradicional sem traço
            "bra2e19",  // Mercosul carro minúsculo
            "BRA2E19",  // Mercosul carro maiúsculo
            "RIO2A18",  // Mercosul carro
            "ABC12D3"   // Mercosul moto
    })
    @DisplayName("Deve aceitar placas válidas (padrão tradicional e Mercosul)")
    void shouldValidateValidPlates(String plate) {
        assertThat(LicensePlateValidator.isValid(plate)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ABCD123",  // 4 letras no início
            "AB12345",  // 2 letras
            "123ABCD",  // invertido
            "ABC-123",  // curto
            "ABC12345", // longo
            "",
            "   "
    })
    @DisplayName("Deve rejeitar formatos de placa inválidos")
    void shouldRejectInvalidPlates(String plate) {
        assertThat(LicensePlateValidator.isValid(plate)).isFalse();
    }

    @Test
    @DisplayName("Deve formatar placas tradicionais com hífen")
    void shouldFormatTraditionalPlate() {
        assertThat(LicensePlateValidator.format("ABC1234")).isEqualTo("ABC-1234");
        assertThat(LicensePlateValidator.format("BRA2E19")).isEqualTo("BRA2E19");
    }
}
