package com.gomech.api.modules.ai.infrastructure.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.gomech.api.modules.ai.api.dto.AiGatewayDtos;
import com.gomech.api.modules.ai.application.AiServiceClient;
import com.gomech.api.modules.ai.domain.AiActionType;
import com.gomech.api.modules.ai.domain.AiException;
import com.gomech.api.modules.ai.domain.AiQuotaExceededException;
import com.gomech.api.modules.ai.domain.AiRateLimitException;
import com.gomech.api.modules.ai.domain.AiServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Component
@Slf4j
public class FastApiAiServiceClient implements AiServiceClient {

    private static final String API_PATH = "/api/v1/ai";
    private static final String SERVICE_AUTH_HEADER = "X-GoMech-Service-Auth";

    private final AiClientConfig config;
    private final RestClient restClient;
    private final RestClient metadataClient;
    private volatile CachedIdToken cachedIdToken;

    public FastApiAiServiceClient(AiClientConfig config) {
        this(config, createRestClient(config.getConnectTimeoutMs(), config.getReadTimeoutMs()),
                createRestClient(1000, 2000));
    }

    FastApiAiServiceClient(AiClientConfig config, RestClient restClient, RestClient metadataClient) {
        this.config = config;
        this.restClient = restClient;
        this.metadataClient = metadataClient;
    }

    @Override
    public AiGatewayDtos.DiagnosticResponse executeDiagnosis(
            UUID tenantId, UUID userId, UUID unitId, String correlationId,
            AiGatewayDtos.DiagnosticRequest request) {
        return executeWithResilience("DIAGNOSTIC_ASSIST", tenantId, correlationId, () -> {
            DiagnosticResponse response = post(
                    "/diagnose", tenantId, userId, unitId, correlationId,
                    new DiagnosticRequest(
                            request.vehicleId(), request.vehicleMake(), request.vehicleModel(),
                            request.vehicleYear(), request.odometerKm(), request.symptomsDescription(),
                            request.faultCodes(), request.customerNotes()),
                    DiagnosticResponse.class);

            return AiGatewayDtos.DiagnosticResponse.builder()
                    .diagnosisSummary(response.diagnosisSummary())
                    .probableCauses(response.probableCauses())
                    .recommendedInspectionSteps(response.recommendedInspectionSteps())
                    .confidenceScore(response.confidenceScore())
                    .proposedAction(toActionType(response.proposedAction()))
                    .proposedChecklist(response.proposedChecklist())
                    .usage(toUsage(response.usage()))
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.QuoteProposalResponse executeQuoteGeneration(
            UUID tenantId, UUID userId, UUID unitId, String correlationId,
            AiGatewayDtos.QuoteGenerationRequest request) {
        return executeWithResilience("QUOTE_GENERATION", tenantId, correlationId, () -> {
            QuoteProposalResponse response = post(
                    "/generate-quote-items", tenantId, userId, unitId, correlationId,
                    new QuoteGenerationRequest(
                            request.workOrderId(), request.vehicleInfo(), request.diagnosticSummary(),
                            request.customerBudgetLimit()),
                    QuoteProposalResponse.class);

            List<AiGatewayDtos.ProposedQuoteItemDto> items = response.proposedItems().stream()
                    .map(item -> AiGatewayDtos.ProposedQuoteItemDto.builder()
                            .type(item.type())
                            .description(item.description())
                            .partNumber(item.partNumber())
                            .quantity(item.quantity())
                            .estimatedPrice(item.estimatedPrice())
                            .rationale(item.rationale())
                            .build())
                    .toList();

            return AiGatewayDtos.QuoteProposalResponse.builder()
                    .summary(response.summary())
                    .proposedItems(items)
                    .estimatedTotalLabor(response.estimatedTotalLabor())
                    .estimatedTotalParts(response.estimatedTotalParts())
                    .totalEstimate(response.totalEstimate())
                    .usage(toUsage(response.usage()))
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.WorkOrderSummaryResponse executeWorkOrderSummary(
            UUID tenantId, UUID userId, UUID unitId, String correlationId,
            AiGatewayDtos.WorkOrderSummaryRequest request) {
        return executeWithResilience("WORK_ORDER_SUMMARY", tenantId, correlationId, () -> {
            WorkOrderSummaryResponse response = post(
                    "/summarize-work-order", tenantId, userId, unitId, correlationId,
                    new WorkOrderSummaryRequest(
                            request.workOrderId(), request.orderNumber(), request.vehicleSummary(),
                            request.customerReportedIssues(), request.servicesPerformed(),
                            request.partsReplaced(), request.mechanicNotes()),
                    WorkOrderSummaryResponse.class);

            return AiGatewayDtos.WorkOrderSummaryResponse.builder()
                    .technicalSummary(response.technicalSummary())
                    .executiveCustomerSummary(response.executiveCustomerSummary())
                    .preventiveRecommendations(response.preventiveRecommendations())
                    .warrantyTerms(response.warrantyTerms())
                    .usage(toUsage(response.usage()))
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.CustomerMessageDraftResponse executeCustomerMessageDraft(
            UUID tenantId, UUID userId, UUID unitId, String correlationId,
            AiGatewayDtos.CustomerMessageDraftRequest request) {
        return executeWithResilience("CUSTOMER_MESSAGE_DRAFT", tenantId, correlationId, () -> {
            CustomerMessageDraftResponse response = post(
                    "/draft-message", tenantId, userId, unitId, correlationId,
                    new CustomerMessageDraftRequest(
                            request.customerId(), request.customerName(), request.vehiclePlate(),
                            request.topic(), request.keyDetails(), request.tone()),
                    CustomerMessageDraftResponse.class);

            return AiGatewayDtos.CustomerMessageDraftResponse.builder()
                    .channel(response.channel())
                    .subject(response.subject())
                    .bodyMessage(response.bodyMessage())
                    .usage(toUsage(response.usage()))
                    .build();
        });
    }

    @Override
    public AiGatewayDtos.GeneralCompletionResponse executeGeneralCompletion(
            UUID tenantId, UUID userId, UUID unitId, String correlationId,
            AiGatewayDtos.GeneralCompletionRequest request) {
        return executeWithResilience("GENERAL_COMPLETION", tenantId, correlationId, () -> {
            ChatResponse response = post(
                    "/chat", tenantId, userId, unitId, correlationId,
                    new ChatRequest(
                            List.of(new ChatMessage("user", request.prompt())),
                            null,
                            request.maxTokens() != null ? request.maxTokens() : 500,
                            request.temperature() != null ? request.temperature() : 0.7),
                    ChatResponse.class);

            return AiGatewayDtos.GeneralCompletionResponse.builder()
                    .completionText(response.reply())
                    .finishReason("STOP")
                    .usage(toUsage(response.usage()))
                    .build();
        });
    }

    private <T> T post(
            String path, UUID tenantId, UUID userId, UUID unitId, String correlationId,
            Object request, Class<T> responseType) {
        if (tenantId == null) {
            throw new AiException("O contexto do tenant é obrigatório para chamar o serviço de IA.",
                    "AI_TENANT_CONTEXT_REQUIRED");
        }

        try {
            T response = restClient.post()
                    .uri(baseUrl() + API_PATH + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> addServiceHeaders(headers, tenantId, userId, unitId, correlationId))
                    .body(request)
                    .retrieve()
                    .body(responseType);

            if (response == null) {
                throw new AiException("O serviço FastAPI retornou uma resposta vazia.", "AI_SERVICE_EMPTY_RESPONSE");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw mapResponseError(exception);
        } catch (ResourceAccessException exception) {
            throw new AiServiceUnavailableException("Não foi possível conectar ao serviço FastAPI.", exception);
        } catch (RestClientException exception) {
            throw new AiException("O serviço FastAPI retornou uma resposta inválida.", exception,
                    "AI_SERVICE_INVALID_RESPONSE");
        }
    }

    private void addServiceHeaders(
            HttpHeaders headers, UUID tenantId, UUID userId, UUID unitId, String correlationId) {
        if (config.getServiceSecret() == null || config.getServiceSecret().isBlank()) {
            throw new AiException("O segredo de autenticação do serviço de IA não foi configurado.",
                    "AI_SERVICE_AUTH_NOT_CONFIGURED");
        }

        headers.set(SERVICE_AUTH_HEADER, config.getServiceSecret());
        headers.set("X-Tenant-Id", tenantId.toString());
        setIfPresent(headers, "X-User-Id", userId);
        setIfPresent(headers, "X-Unit-Id", unitId);
        if (correlationId != null && !correlationId.isBlank()) {
            headers.set("X-Correlation-Id", correlationId);
        }

        String idToken = getCloudRunIdToken();
        if (idToken != null) {
            headers.setBearerAuth(idToken);
        }
    }

    private String getCloudRunIdToken() {
        String audience = config.getIdTokenAudience();
        if (audience == null || audience.isBlank()) {
            return null;
        }

        long now = System.currentTimeMillis();
        CachedIdToken current = cachedIdToken;
        if (current != null && current.refreshAfterMillis() > now) {
            return current.value();
        }

        synchronized (this) {
            current = cachedIdToken;
            if (current != null && current.refreshAfterMillis() > now) {
                return current.value();
            }

            try {
                String token = metadataClient.get()
                        .uri(uriBuilder -> uriBuilder
                                .scheme("http")
                                .host("metadata.google.internal")
                                .path("/computeMetadata/v1/instance/service-accounts/default/identity")
                                .queryParam("audience", audience)
                                .queryParam("format", "full")
                                .build())
                        .header("Metadata-Flavor", "Google")
                        .retrieve()
                        .body(String.class);

                if (token == null || token.isBlank()) {
                    throw new AiServiceUnavailableException("O metadata server não retornou um token para o Cloud Run.");
                }

                cachedIdToken = new CachedIdToken(token, now + Duration.ofMinutes(50).toMillis());
                return token;
            } catch (RestClientException exception) {
                throw new AiServiceUnavailableException("Não foi possível obter o token IAM do serviço FastAPI.",
                        exception);
            }
        }
    }

    private RuntimeException mapResponseError(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 429) {
            long retryAfter = 5;
            String header = exception.getResponseHeaders() != null
                    ? exception.getResponseHeaders().getFirst("Retry-After")
                    : null;
            if (header != null) {
                try {
                    retryAfter = Math.max(Long.parseLong(header), 0);
                } catch (NumberFormatException ignored) {
                    retryAfter = 5;
                }
            }
            return new AiRateLimitException("O serviço de IA atingiu o limite de solicitações.", retryAfter);
        }
        if (status == 402) {
            return new AiQuotaExceededException("A cota do provedor de IA foi excedida.");
        }
        if (status >= 500) {
            return new AiServiceUnavailableException(
                    "O serviço FastAPI respondeu com erro HTTP " + status + ".", exception);
        }
        if (status == 401 || status == 403) {
            return new AiException("A autenticação entre o backend e o serviço FastAPI foi rejeitada.",
                    "AI_SERVICE_AUTH_FAILED");
        }
        return new AiException("O serviço FastAPI rejeitou a solicitação (HTTP " + status + ").",
                "AI_SERVICE_REQUEST_REJECTED");
    }

    private <T> T executeWithResilience(String operation, UUID tenantId, String correlationId, Supplier<T> call) {
        int maxAttempts = Math.max(config.getMaxRetries(), 1);
        long baseBackoffMs = Math.max(config.getBackoffBaseMs(), 50);

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug("Chamando FastAPI [{}] tentativa={}/{} tenant={} correlationId={}",
                        operation, attempt, maxAttempts, tenantId, correlationId);
                return call.get();
            } catch (AiRateLimitException | AiQuotaExceededException exception) {
                throw exception;
            } catch (AiServiceUnavailableException exception) {
                if (attempt == maxAttempts) {
                    log.error("FastAPI indisponível após {} tentativas para {} tenant={}",
                            maxAttempts, operation, tenantId, exception);
                    throw exception;
                }

                long backoffMs = (long) (baseBackoffMs * Math.pow(2, attempt - 1))
                        + ThreadLocalRandom.current().nextLong(10, 50);
                log.warn("Falha transitória chamando FastAPI ({}/{}); nova tentativa em {}ms",
                        attempt, maxAttempts, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AiException("Interrupção durante a chamada ao serviço FastAPI.", interrupted,
                            "AI_GATEWAY_INTERRUPTED");
                }
            }
        }

        throw new AiServiceUnavailableException("Serviço FastAPI indisponível após retentativas.");
    }

    private String baseUrl() {
        String baseUrl = config.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new AiException("A URL do serviço FastAPI não foi configurada.", "AI_SERVICE_URL_NOT_CONFIGURED");
        }
        return baseUrl.replaceAll("/+$", "");
    }

    private static RestClient createRestClient(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(connectTimeoutMs, 1));
        requestFactory.setReadTimeout(Math.max(readTimeoutMs, 1));
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static void setIfPresent(HttpHeaders headers, String name, UUID value) {
        if (value != null) {
            headers.set(name, value.toString());
        }
    }

    private static AiActionType toActionType(String action) {
        if (action == null || action.isBlank()) {
            return AiActionType.NONE;
        }
        try {
            return AiActionType.valueOf(action);
        } catch (IllegalArgumentException ignored) {
            return AiActionType.NONE;
        }
    }

    private static AiGatewayDtos.UsageMetadata toUsage(UsageMetadata usage) {
        if (usage == null) {
            throw new AiException("A resposta FastAPI não contém metadados de uso.",
                    "AI_SERVICE_INVALID_RESPONSE");
        }
        return AiGatewayDtos.UsageMetadata.builder()
                .modelUsed(usage.modelUsed())
                .promptTokens(usage.promptTokens())
                .completionTokens(usage.completionTokens())
                .totalTokens(usage.totalTokens())
                .latencyMs(usage.latencyMs())
                .build();
    }

    private record CachedIdToken(String value, long refreshAfterMillis) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record UsageMetadata(String modelUsed, int promptTokens, int completionTokens, int totalTokens, long latencyMs) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record DiagnosticRequest(
            UUID vehicleId, String vehicleMake, String vehicleModel, Integer vehicleYear, Integer odometerKm,
            String symptomsDescription, List<String> faultCodes, String customerNotes) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record DiagnosticResponse(
            String diagnosisSummary, List<String> probableCauses, List<String> recommendedInspectionSteps,
            Double confidenceScore, String proposedAction, List<String> proposedChecklist,
            List<String> safetyWarnings, UsageMetadata usage) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record QuoteGenerationRequest(
            UUID workOrderId, String vehicleInfo, String diagnosticSummary, BigDecimal customerBudgetLimit) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ProposedQuoteItem(
            String type, String description, String partNumber, BigDecimal quantity, BigDecimal estimatedPrice,
            String rationale) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record QuoteProposalResponse(
            String summary, List<ProposedQuoteItem> proposedItems, BigDecimal estimatedTotalLabor,
            BigDecimal estimatedTotalParts, BigDecimal totalEstimate, UsageMetadata usage) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record WorkOrderSummaryRequest(
            UUID workOrderId, String orderNumber, String vehicleSummary, String customerReportedIssues,
            List<String> servicesPerformed, List<String> partsReplaced, String mechanicNotes) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record WorkOrderSummaryResponse(
            String technicalSummary, String executiveCustomerSummary, List<String> preventiveRecommendations,
            String warrantyTerms, UsageMetadata usage) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record CustomerMessageDraftRequest(
            UUID customerId, String customerName, String vehiclePlate, String topic, String keyDetails, String tone) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record CustomerMessageDraftResponse(
            String channel, String subject, String bodyMessage, UsageMetadata usage) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ChatMessage(String role, String content) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ChatRequest(List<ChatMessage> messages, String contextVehicle, Integer maxTokens, Double temperature) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    private record ChatResponse(String reply, UsageMetadata usage) {}
}

