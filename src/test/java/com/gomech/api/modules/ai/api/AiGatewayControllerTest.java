package com.gomech.api.modules.ai.api;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.application.ActorContextProvider;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.tenancy.UnitReference;
import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import com.gomech.api.modules.ai.application.AiGatewayService;
import com.gomech.api.modules.ai.domain.AiActionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGatewayControllerTest {

    @Mock
    private AiGatewayService aiGatewayService;

    @Mock
    private ActorContextProvider actorContextProvider;

    @InjectMocks
    private AiGatewayController controller;

    private UUID tenantId;
    private UUID userId;
    private UUID unitId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
        lenient().when(actorContextProvider.currentActor())
                .thenReturn(Optional.of(new ActorContext(userId, tenantId, UnitReference.of(unitId), Set.of("AI_QUERY", "AI_ACTION_EXECUTE"), Set.of())));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("POST /api/v1/ai/diagnose - Deve retornar diagnóstico assistido com sucesso")
    void shouldDiagnoseSuccessfully() {
        AiGatewayDtos.DiagnosticResponse mockResponse = AiGatewayDtos.DiagnosticResponse.builder()
                .diagnosisSummary("Falha de ignição identificada")
                .probableCauses(List.of("Velas desgastadas"))
                .confidenceScore(0.95)
                .proposedAction(AiActionType.PROPOSE_QUOTE_ITEMS)
                .usage(AiGatewayDtos.UsageMetadata.builder()
                        .modelUsed("gomech-reasoning-pro")
                        .totalTokens(300)
                        .latencyMs(100L)
                        .build())
                .build();

        AiGatewayDtos.DiagnosticRequest request = AiGatewayDtos.DiagnosticRequest.builder()
                .symptomsDescription("Motor engasgando em retomadas")
                .build();

        when(aiGatewayService.diagnoseVehicle(eq(tenantId), eq(userId), eq(unitId), any(AiGatewayDtos.DiagnosticRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<AiGatewayDtos.DiagnosticResponse> response = controller.diagnose(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().diagnosisSummary()).isEqualTo("Falha de ignição identificada");
        assertThat(response.getBody().usage().totalTokens()).isEqualTo(300);
        verify(aiGatewayService).diagnoseVehicle(eq(tenantId), eq(userId), eq(unitId), any(AiGatewayDtos.DiagnosticRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/ai/generate-quote-items - Deve retornar proposição de orçamento com sucesso")
    void shouldGenerateQuoteItemsSuccessfully() {
        AiGatewayDtos.QuoteProposalResponse mockResponse = AiGatewayDtos.QuoteProposalResponse.builder()
                .summary("Orçamento prévio gerado")
                .estimatedTotalLabor(BigDecimal.valueOf(150.00))
                .estimatedTotalParts(BigDecimal.valueOf(200.00))
                .totalEstimate(BigDecimal.valueOf(350.00))
                .proposedItems(List.of(
                        AiGatewayDtos.ProposedQuoteItemDto.builder()
                                .type("PART")
                                .description("Velas de Ignição")
                                .estimatedPrice(BigDecimal.valueOf(200.00))
                                .build()
                ))
                .usage(AiGatewayDtos.UsageMetadata.builder()
                        .modelUsed("gomech-reasoning-pro")
                        .totalTokens(350)
                        .latencyMs(110L)
                        .build())
                .build();

        AiGatewayDtos.QuoteGenerationRequest request = AiGatewayDtos.QuoteGenerationRequest.builder()
                .workOrderId(UUID.randomUUID())
                .diagnosticSummary("Velas com desgaste excessivo")
                .build();

        when(aiGatewayService.generateQuoteProposal(eq(tenantId), eq(userId), eq(unitId), any(AiGatewayDtos.QuoteGenerationRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<AiGatewayDtos.QuoteProposalResponse> response = controller.generateQuoteItems(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().totalEstimate()).isEqualTo(BigDecimal.valueOf(350.00));
        assertThat(response.getBody().proposedItems().get(0).description()).isEqualTo("Velas de Ignição");
        verify(aiGatewayService).generateQuoteProposal(eq(tenantId), eq(userId), eq(unitId), any(AiGatewayDtos.QuoteGenerationRequest.class));
    }

    @Test
    @DisplayName("GET /api/v1/ai/usage - Deve retornar status de consumo de cota de IA")
    void shouldGetUsageStatusSuccessfully() {
        AiGatewayDtos.AiUsageStatusResponse mockResponse = AiGatewayDtos.AiUsageStatusResponse.builder()
                .tenantId(tenantId)
                .planCode("PRO")
                .quotaLimit(5000L)
                .quotaUsed(150L)
                .quotaRemaining(4850L)
                .isAllowed(true)
                .build();

        when(aiGatewayService.getUsageStatus(eq(tenantId))).thenReturn(mockResponse);

        ResponseEntity<AiGatewayDtos.AiUsageStatusResponse> response = controller.getUsageStatus();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().quotaLimit()).isEqualTo(5000L);
        assertThat(response.getBody().quotaUsed()).isEqualTo(150L);
        assertThat(response.getBody().isAllowed()).isTrue();
        verify(aiGatewayService).getUsageStatus(eq(tenantId));
    }
}
