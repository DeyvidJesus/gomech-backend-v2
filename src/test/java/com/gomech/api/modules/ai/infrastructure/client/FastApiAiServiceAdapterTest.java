package com.gomech.api.modules.ai.infrastructure.client;

import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class FastApiAiServiceAdapterTest {

    private AiClientConfig config;
    private FastApiAiServiceAdapter client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        config = new AiClientConfig();
        config.setBaseUrl("http://ai");
        config.setServiceSecret("test-service-secret");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new FastApiAiServiceAdapter(config, builder.build(), RestClient.builder().build());
    }

    @Test
    void shouldSendDiagnosisToFastApiAndMapResponse() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        server.expect(requestTo("http://ai/api/v1/ai/diagnose"))
                .andExpect(method(POST))
                .andExpect(header("X-GoMech-Service-Auth", "test-service-secret"))
                .andExpect(header("X-Tenant-Id", tenantId.toString()))
                .andExpect(header("X-User-Id", userId.toString()))
                .andRespond(withSuccess("""
                        {
                          "diagnosis_summary": "Falha de ignição provável",
                          "probable_causes": ["Velas desgastadas"],
                          "recommended_inspection_steps": ["Inspecionar velas"],
                          "confidence_score": 0.9,
                          "proposed_action": "PROPOSE_QUOTE_ITEMS",
                          "proposed_checklist": ["Velas"],
                          "safety_warnings": [],
                          "usage": {
                            "model_used": "gemini-1.5-flash",
                            "prompt_tokens": 20,
                            "completion_tokens": 30,
                            "total_tokens": 50,
                            "latency_ms": 80
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiGatewayDtos.DiagnosticRequest request = AiGatewayDtos.DiagnosticRequest.builder()
                .symptomsDescription("Motor falhando em subidas")
                .faultCodes(List.of("P0300"))
                .build();

        AiGatewayDtos.DiagnosticResponse response = client.executeDiagnosis(
                tenantId, userId, null, "corr-test-1", request);

        assertNotNull(response);
        assertEquals("Falha de ignição provável", response.diagnosisSummary());
        assertEquals("gemini-1.5-flash", response.usage().modelUsed());
        assertEquals(50, response.usage().totalTokens());
        server.verify();
    }

    @Test
    void shouldSendQuoteGenerationToFastApi() {
        UUID tenantId = UUID.randomUUID();

        server.expect(requestTo("http://ai/api/v1/ai/generate-quote-items"))
                .andExpect(method(POST))
                .andExpect(header("X-Tenant-Id", tenantId.toString()))
                .andRespond(withSuccess("""
                        {
                          "summary": "Proposta de orçamento",
                          "proposed_items": [
                            {
                              "type": "PART",
                              "description": "Jogo de velas",
                              "part_number": "NGK-01",
                              "quantity": 4,
                              "estimated_price": 120.00,
                              "rationale": "Inspeção recomendada"
                            }
                          ],
                          "estimated_total_labor": 80.00,
                          "estimated_total_parts": 120.00,
                          "total_estimate": 200.00,
                          "usage": {
                            "model_used": "mock-automotive-intelligence",
                            "prompt_tokens": 10,
                            "completion_tokens": 20,
                            "total_tokens": 30,
                            "latency_ms": 25
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        AiGatewayDtos.QuoteGenerationRequest request = AiGatewayDtos.QuoteGenerationRequest.builder()
                .vehicleInfo("Honda Civic 2018")
                .diagnosticSummary("Desgaste de velas")
                .build();

        AiGatewayDtos.QuoteProposalResponse response = client.executeQuoteGeneration(
                tenantId, null, null, "corr-test-2", request);

        assertFalse(response.proposedItems().isEmpty());
        assertEquals(new java.math.BigDecimal("200.00"), response.totalEstimate());
        server.verify();
    }
}
