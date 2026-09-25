package com.gomech.api.modules.ai.infrastructure.client;

import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import com.gomech.api.modules.ai.application.AiServiceClient;
import com.gomech.api.modules.ai.domain.AiActionType;
import com.gomech.api.modules.ai.domain.AiException;
import com.gomech.api.modules.ai.domain.AiQuotaExceededException;
import com.gomech.api.modules.ai.domain.AiRateLimitException;
import com.gomech.api.modules.ai.domain.AiServiceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultAiServiceExecutor implements AiServiceClient {

    private final AiClientConfig config;

    @Override
    public AiGatewayDtos.DiagnosticResponse executeDiagnosis(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.DiagnosticRequest request) {
        return executeWithResilience("DIAGNOSTIC_ASSIST", tenantId, userId, unitId, correlationId, () -> {
            // High-fidelity structured AI diagnostic simulation
            long start = System.currentTimeMillis();
            List<String> faultCodes = request.faultCodes() != null ? request.faultCodes() : List.of();
            String symptoms = request.symptomsDescription() != null ? request.symptomsDescription().toLowerCase() : "";

            String diagSummary;
            List<String> causes;
            List<String> steps;
            List<String> checklist;

            if (faultCodes.contains("P0300") || symptoms.contains("falhando") || symptoms.contains("engasgando")) {
                diagSummary = "Identificado padrão de falha de ignição/injeção intermitente no ciclo de combustão.";
                causes = List.of(
                        "Desgaste excessivo nas velas de ignição",
                        "Fuga de corrente nos cabos de vela ou bobinas de ignição",
                        "Bicos injetores carbonizados ou com vazão irregular",
                        "Entrada falsa de ar no coletor de admissão"
                );
                steps = List.of(
                        "1. Efetuar teste de centelhamento e resistência ohmica das bobinas de ignição.",
                        "2. Inspecionar estado dos eletrodos e folga das velas de ignição.",
                        "3. Medir pressão e vazão da linha de combustível (bomba e regulador).",
                        "4. Realizar teste de estanqueidade e equalização dos bicos injetores em bancada."
                );
                checklist = List.of("Jogo de Velas", "Jogo de Cabos/Bobinas", "Limpeza de Bicos", "Filtro de Combustível");
            } else if (symptoms.contains("freio") || symptoms.contains("barulho ao frear") || symptoms.contains("chiado")) {
                diagSummary = "Anomalia no sistema de fricção/frenagem dianteiro/traseiro.";
                causes = List.of(
                        "Pastilhas de freio no limite de desgaste (contato do indicador acústico)",
                        "Discos de freio empenados ou com sulcos profundos",
                        "Fluido de freio contaminado com umidade (> 3%)"
                );
                steps = List.of(
                        "1. Medir espessura das pastilhas e espessura residual dos discos com micrômetro.",
                        "2. Testar ponto de ebulição do fluido de freio DOT 4.",
                        "3. Verificar pinças e flexíveis contra vazamentos ou travamentos."
                );
                checklist = List.of("Pastilhas de Freio Dianteiras", "Discos de Freio Dianteiros", "Fluido de Freio DOT 4");
            } else {
                diagSummary = "Padrão de anomalia mecânica/elétrica geral identificado a partir dos sintomas relatados.";
                causes = List.of(
                        "Desgaste natural de componentes da suspensão ou rodagem",
                        "Nível ou especificação incorreta de lubrificantes/fluidos",
                        "Conexões elétricas ou sensores com leitura fora da faixa operacional"
                );
                steps = List.of(
                        "1. Realizar inspeção visual em elevador automotivo e teste de folgas.",
                        "2. Conectar scanner OBD-II e analisar parâmetros dinâmicos em tempo real.",
                        "3. Executar teste de rodagem monitorado."
                );
                checklist = List.of("Checklist de Inspeção de Suspensão", "Diagnóstico Eletrônico com Scanner");
            }

            long latency = System.currentTimeMillis() - start + 80;
            return AiGatewayDtos.DiagnosticResponse.builder()
                    .diagnosisSummary(diagSummary)
                    .probableCauses(causes)
                    .recommendedInspectionSteps(steps)
                    .confidenceScore(0.92)
                    .proposedAction(AiActionType.PROPOSE_QUOTE_ITEMS)
                    .proposedChecklist(checklist)
                    .usage(AiGatewayDtos.UsageMetadata.builder()
                            .modelUsed("gomech-reasoning-pro")
                            .promptTokens(180)
                            .completionTokens(220)
                            .totalTokens(400)
                            .latencyMs(latency)
                            .build())
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.QuoteProposalResponse executeQuoteGeneration(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.QuoteGenerationRequest request) {
        return executeWithResilience("QUOTE_GENERATION", tenantId, userId, unitId, correlationId, () -> {
            long start = System.currentTimeMillis();

            List<AiGatewayDtos.ProposedQuoteItemDto> items = List.of(
                    AiGatewayDtos.ProposedQuoteItemDto.builder()
                            .type("SERVICE")
                            .description("Mão de Obra: Diagnóstico Computadorizado e Revisão de Ignição/Injeção")
                            .partNumber("MO-DIAG-01")
                            .quantity(BigDecimal.ONE)
                            .estimatedPrice(BigDecimal.valueOf(180.00))
                            .rationale("Tempo estimado de bancada e calibração: 1.5 horas")
                            .build(),
                    AiGatewayDtos.ProposedQuoteItemDto.builder()
                            .type("PART")
                            .description("Jogo de Velas de Ignição Iridium")
                            .partNumber("NGK-BKR6EIX")
                            .quantity(BigDecimal.valueOf(4))
                            .estimatedPrice(BigDecimal.valueOf(240.00))
                            .rationale("Substituição preventiva recomendada pelo diagnóstico")
                            .build(),
                    AiGatewayDtos.ProposedQuoteItemDto.builder()
                            .type("PART")
                            .description("Filtro de Combustível Injeção")
                            .partNumber("FIL-FC-120")
                            .quantity(BigDecimal.ONE)
                            .estimatedPrice(BigDecimal.valueOf(45.00))
                            .rationale("Troca periódica para proteção do sistema")
                            .build()
            );

            BigDecimal labor = BigDecimal.valueOf(180.00);
            BigDecimal parts = BigDecimal.valueOf(285.00);
            BigDecimal total = labor.add(parts);

            long latency = System.currentTimeMillis() - start + 95;
            return AiGatewayDtos.QuoteProposalResponse.builder()
                    .summary("Proposta gerada automaticamente com base no diagnóstico técnico de ignição e injeção.")
                    .proposedItems(items)
                    .estimatedTotalLabor(labor)
                    .estimatedTotalParts(parts)
                    .totalEstimate(total)
                    .usage(AiGatewayDtos.UsageMetadata.builder()
                            .modelUsed("gomech-reasoning-pro")
                            .promptTokens(210)
                            .completionTokens(260)
                            .totalTokens(470)
                            .latencyMs(latency)
                            .build())
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.WorkOrderSummaryResponse executeWorkOrderSummary(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.WorkOrderSummaryRequest request) {
        return executeWithResilience("WORK_ORDER_SUMMARY", tenantId, userId, unitId, correlationId, () -> {
            long start = System.currentTimeMillis();

            String techSummary = "OS #" + (request.orderNumber() != null ? request.orderNumber() : "") +
                    ": Executados serviços de revisão conforme laudo. Veículo testado e aprovado nos parâmetros dinâmicos.";
            String execSummary = "Olá! Seu veículo foi revisado com sucesso por nossa equipe técnica. Os itens de segurança e desempenho foram calibrados e estão 100% operacionais.";

            List<String> recs = List.of(
                    "Revisão preventiva programada para daqui a 10.000 km ou 6 meses.",
                    "Checagem semanal da calibragem dos pneus (32 PSI)."
            );

            long latency = System.currentTimeMillis() - start + 65;
            return AiGatewayDtos.WorkOrderSummaryResponse.builder()
                    .technicalSummary(techSummary)
                    .executiveCustomerSummary(execSummary)
                    .preventiveRecommendations(recs)
                    .warrantyTerms("Garantia legal de 90 dias sobre peças aplicadas e serviços executados.")
                    .usage(AiGatewayDtos.UsageMetadata.builder()
                            .modelUsed("gomech-turbo-fast")
                            .promptTokens(140)
                            .completionTokens(160)
                            .totalTokens(300)
                            .latencyMs(latency)
                            .build())
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.CustomerMessageDraftResponse executeCustomerMessageDraft(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.CustomerMessageDraftRequest request) {
        return executeWithResilience("CUSTOMER_MESSAGE_DRAFT", tenantId, userId, unitId, correlationId, () -> {
            long start = System.currentTimeMillis();

            String subject = "GoMech: Atualização do seu veículo " + (request.vehiclePlate() != null ? "(" + request.vehiclePlate() + ")" : "");
            String customerName = request.customerName() != null ? request.customerName() : "Cliente";

            String body;
            if ("QUOTE_READY".equalsIgnoreCase(request.topic())) {
                body = "Olá, " + customerName + "! O orçamento detalhado para a manutenção do seu veículo já está pronto para sua análise e aprovação. Acesse o link seguro do portal ou responda esta mensagem para tirar dúvidas.";
            } else if ("SERVICE_COMPLETED".equalsIgnoreCase(request.topic())) {
                body = "Olá, " + customerName + "! Boas notícias: o serviço no seu veículo foi concluído com sucesso e o carro já está pronto para retirada em nossa oficina!";
            } else {
                body = "Olá, " + customerName + "! Temos uma atualização importante sobre o andamento dos serviços no seu veículo: " + (request.keyDetails() != null ? request.keyDetails() : "Aguardamos sua confirmação.");
            }

            long latency = System.currentTimeMillis() - start + 50;
            return AiGatewayDtos.CustomerMessageDraftResponse.builder()
                    .channel("WHATSAPP")
                    .subject(subject)
                    .bodyMessage(body)
                    .usage(AiGatewayDtos.UsageMetadata.builder()
                            .modelUsed("gomech-turbo-fast")
                            .promptTokens(90)
                            .completionTokens(110)
                            .totalTokens(200)
                            .latencyMs(latency)
                            .build())
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.GeneralCompletionResponse executeGeneralCompletion(
            UUID tenantId, UUID userId, UUID unitId, String correlationId, AiGatewayDtos.GeneralCompletionRequest request) {
        return executeWithResilience("GENERAL_COMPLETION", tenantId, userId, unitId, correlationId, () -> {
            long start = System.currentTimeMillis();

            long latency = System.currentTimeMillis() - start + 40;
            return AiGatewayDtos.GeneralCompletionResponse.builder()
                    .completionText("Processamento concluído com sucesso pelo GoMech AI Engine.")
                    .finishReason("STOP")
                    .usage(AiGatewayDtos.UsageMetadata.builder()
                            .modelUsed(request.model() != null ? request.model().getModelName() : "gomech-turbo-fast")
                            .promptTokens(50)
                            .completionTokens(50)
                            .totalTokens(100)
                            .latencyMs(latency)
                            .build())
                    .build();
        });
    }

    private <T> T executeWithResilience(
            String operation, UUID tenantId, UUID userId, UUID unitId, String correlationId, SupplierWithException<T> supplier) {
        int maxRetries = Math.max(config.getMaxRetries(), 1);
        long baseBackoff = Math.max(config.getBackoffBaseMs(), 50);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("Executando AI Gateway [{}] attempt={}/{} tenant={} correlationId={}",
                        operation, attempt, maxRetries, tenantId, correlationId);
                return supplier.get();
            } catch (AiRateLimitException | AiQuotaExceededException e) {
                // Rejeições de cota ou rate limit explícito não sofrem retry cego
                throw e;
            } catch (Exception ex) {
                if (attempt == maxRetries) {
                    log.error("AI Gateway esgotou tentativas ({}) para operação {} no tenant {}: {}",
                            maxRetries, operation, tenantId, ex.getMessage(), ex);
                    throw new AiServiceUnavailableException("Serviço de Inteligência Artificial temporariamente indisponível. Tente novamente.", ex);
                }

                long backoff = (long) (baseBackoff * Math.pow(2, attempt - 1)) + ThreadLocalRandom.current().nextLong(10, 50);
                log.warn("AI Gateway falha transitória (tentativa {}/{}). Aguardando {}ms para retry: {}",
                        attempt, maxRetries, backoff, ex.getMessage());
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new AiException("Interrupção durante retry do AI Gateway", ie, "AI_GATEWAY_INTERRUPTED");
                }
            }
        }

        throw new AiServiceUnavailableException("Serviço de IA indisponível após retentativas.");
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
