package com.gomech.api.modules.ai.application;

import com.gomech.api.core.audit.application.AuditRecorder;
import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import com.gomech.api.modules.ai.domain.AiActionType;
import com.gomech.api.modules.ai.domain.AiQuotaExceededException;
import com.gomech.api.modules.ai.events.AiRequestAuditedEvent;
import com.gomech.api.modules.ai.infrastructure.metrics.AiGatewayMetrics;
import com.gomech.api.modules.ai.infrastructure.persistence.entity.AiGatewayAuditLog;
import com.gomech.api.modules.ai.infrastructure.persistence.repository.AiGatewayAuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiGatewayServiceTest {

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private AiGatewayAuditLogRepository auditLogRepository;

    @Mock
    private AiGatewayMetrics metrics;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private DomainEventBus eventBus;

    @InjectMocks
    private AiGatewayService aiGatewayService;

    private UUID tenantId;
    private UUID userId;
    private UUID unitId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        unitId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve bloquear requisição de IA e lançar AiQuotaExceededException quando a cota estiver esgotada")
    void shouldBlockRequestWhenQuotaExceeded() {
        when(entitlementService.checkQuota(eq(tenantId), eq(QuotaDimension.AI_USAGE), anyLong()))
                .thenReturn(QuotaDecision.deny(QuotaDimension.AI_USAGE, 500, 500, "Cota mensal de 500 chamadas esgotada. Faça upgrade do plano."));

        AiGatewayDtos.DiagnosticRequest request = AiGatewayDtos.DiagnosticRequest.builder()
                .symptomsDescription("Barulho metálico na suspensão dianteira")
                .build();

        assertThrows(AiQuotaExceededException.class, () ->
                aiGatewayService.diagnoseVehicle(tenantId, userId, unitId, request));

        // Garantir que o serviço remoto de IA NUNCA foi chamado
        verifyNoInteractions(aiServiceClient);
        // Garantir que a tentativa rejeitada foi gravada para auditoria
        verify(auditLogRepository).save(any(AiGatewayAuditLog.class));
        verify(metrics).recordError(eq("DIAGNOSTIC_ASSIST"), eq("REJECTED_QUOTA_EXCEEDED"), eq("AI_QUOTA_EXCEEDED"));
    }

    @Test
    @DisplayName("Deve executar diagnóstico com sucesso, sanitizar PII e debitar cota")
    void shouldExecuteDiagnosisAndSanitizePii() {
        when(entitlementService.checkQuota(eq(tenantId), eq(QuotaDimension.AI_USAGE), anyLong()))
                .thenReturn(QuotaDecision.allow(QuotaDimension.AI_USAGE, 10, 500, "Cota disponível"));

        AiGatewayDtos.DiagnosticResponse mockResponse = AiGatewayDtos.DiagnosticResponse.builder()
                .diagnosisSummary("Folga identificada nas buchas da barra estabilizadora.")
                .probableCauses(List.of("Bucha da barra desgastada"))
                .recommendedInspectionSteps(List.of("Checar folga no elevador"))
                .confidenceScore(0.95)
                .proposedAction(AiActionType.PROPOSE_QUOTE_ITEMS)
                .usage(AiGatewayDtos.UsageMetadata.builder()
                        .modelUsed("gomech-reasoning-pro")
                        .promptTokens(100)
                        .completionTokens(150)
                        .totalTokens(250)
                        .latencyMs(120L)
                        .build())
                .build();

        when(aiServiceClient.executeDiagnosis(eq(tenantId), eq(userId), eq(unitId), anyString(), any(AiGatewayDtos.DiagnosticRequest.class)))
                .thenReturn(mockResponse);

        AiGatewayDtos.DiagnosticRequest request = AiGatewayDtos.DiagnosticRequest.builder()
                .symptomsDescription("Cliente CPF 123.456.789-00 reclamou de barulho na suspensão. Contato: joao@gmail.com")
                .customerNotes("Favor ligar no (11) 98765-4321")
                .build();

        AiGatewayDtos.DiagnosticResponse response = aiGatewayService.diagnoseVehicle(tenantId, userId, unitId, request);

        assertNotNull(response);
        assertEquals("Folga identificada nas buchas da barra estabilizadora.", response.diagnosisSummary());

        // Verificar sanitização enviada para o cliente de IA
        ArgumentCaptor<AiGatewayDtos.DiagnosticRequest> reqCaptor = ArgumentCaptor.forClass(AiGatewayDtos.DiagnosticRequest.class);
        verify(aiServiceClient).executeDiagnosis(eq(tenantId), eq(userId), eq(unitId), anyString(), reqCaptor.capture());
        AiGatewayDtos.DiagnosticRequest capturedReq = reqCaptor.getValue();
        assertFalse(capturedReq.symptomsDescription().contains("123.456.789-00"));
        assertFalse(capturedReq.symptomsDescription().contains("joao@gmail.com"));
        assertFalse(capturedReq.customerNotes().contains("98765-4321"));
        assertTrue(capturedReq.symptomsDescription().contains("[CPF_REDACTED]"));
        assertTrue(capturedReq.symptomsDescription().contains("[EMAIL_REDACTED]"));
        assertTrue(capturedReq.customerNotes().contains("[PHONE_REDACTED]"));

        // Verificar débito de cota e auditoria
        verify(entitlementService).recordUsage(tenantId, QuotaDimension.AI_USAGE, 1);
        verify(auditLogRepository).save(any(AiGatewayAuditLog.class));
        verify(auditRecorder).record(any(), any());
        verify(eventBus).publish(any(AiRequestAuditedEvent.class));
        verify(metrics).recordSuccess(eq("DIAGNOSTIC_ASSIST"), eq("gomech-reasoning-pro"), eq(100), eq(150), eq(120L));
    }
}
