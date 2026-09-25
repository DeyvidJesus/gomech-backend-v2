package com.gomech.api.modules.ai.infrastructure.client;

import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AiServiceClientResilienceTest {

    private AiClientConfig config;
    private DefaultAiServiceExecutor executor;

    @BeforeEach
    void setUp() {
        config = new AiClientConfig();
        config.setMaxRetries(3);
        config.setBackoffBaseMs(10);
        config.setMockEnabled(true);
        executor = new DefaultAiServiceExecutor(config);
    }

    @Test
    @DisplayName("Deve executar diagnóstico com sucesso e retornar metadados de uso")
    void shouldExecuteDiagnosisSuccessfully() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AiGatewayDtos.DiagnosticRequest request = AiGatewayDtos.DiagnosticRequest.builder()
                .symptomsDescription("Motor falhando em subidas e barulho de estouro no escapamento")
                .faultCodes(List.of("P0300"))
                .build();

        AiGatewayDtos.DiagnosticResponse response = executor.executeDiagnosis(
                tenantId, userId, null, "corr-test-1", request);

        assertNotNull(response);
        assertNotNull(response.diagnosisSummary());
        assertFalse(response.probableCauses().isEmpty());
        assertNotNull(response.usage());
        assertEquals("gomech-reasoning-pro", response.usage().modelUsed());
        assertTrue(response.usage().totalTokens() > 0);
        assertTrue(response.usage().latencyMs() > 0);
    }

    @Test
    @DisplayName("Deve executar geração de itens de orçamento com sucesso")
    void shouldExecuteQuoteGenerationSuccessfully() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        AiGatewayDtos.QuoteGenerationRequest request = AiGatewayDtos.QuoteGenerationRequest.builder()
                .workOrderId(UUID.randomUUID())
                .diagnosticSummary("Desgaste de velas e bicos injetores carbonizados")
                .build();

        AiGatewayDtos.QuoteProposalResponse response = executor.executeQuoteGeneration(
                tenantId, userId, null, "corr-test-2", request);

        assertNotNull(response);
        assertFalse(response.proposedItems().isEmpty());
        assertNotNull(response.totalEstimate());
        assertTrue(response.usage().totalTokens() > 0);
    }
}
