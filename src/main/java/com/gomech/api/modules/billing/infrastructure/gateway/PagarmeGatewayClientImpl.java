package com.gomech.api.modules.billing.infrastructure.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.billing.application.gateway.PagarmeGatewayClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class PagarmeGatewayClientImpl implements PagarmeGatewayClient {

    private final PagarmeProperties properties;
    private final ObjectMapper objectMapper;

    private String resolveBaseUrl() {
        String customUrl = properties.getApiUrl();
        if (customUrl != null && !customUrl.isBlank() && !customUrl.equals("https://api.pagar.me/core/v5")) {
            return customUrl;
        }
        if (properties.getApiKey() != null && properties.getApiKey().startsWith("sk_test_")) {
            return "https://sdx-api.pagar.me/core/v5";
        }
        return "https://api.pagar.me/core/v5";
    }

    private RestClient getRestClient() {
        String authHeader = "Basic " + Base64.getEncoder().encodeToString((properties.getApiKey() + ":").getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
                .baseUrl(resolveBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .defaultHeader(HttpHeaders.USER_AGENT, "pagarme-skill-generated/1.0")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public PagarmeDto.PaymentLinkResponse createHostedCheckout(PagarmeDto.CreateHostedCheckoutRequest request) {
        log.info("Criando Pagar.me Hosted Checkout para tenant {} no plano {} (Target URL: {})",
                request.tenantId(), request.planCode(), resolveBaseUrl());

        if (properties.isMockEnabled() || properties.getApiKey().contains("placeholder")) {
            return generateMockHostedCheckout(request);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "subscription");
            payload.put("payment_settings", Map.of(
                    "accepted_payment_methods", List.of("credit_card"),
                    "credit_card_settings", Map.of(
                            "operation_type", "auth_and_capture"
                    )
            ));

            Map<String, Object> recurrence = new HashMap<>();
            recurrence.put("plan_id", request.pagarmePlanId());
            payload.put("cart_settings", Map.of(
                    "recurrences", List.of(recurrence)
            ));

            if (request.customerId() != null && !request.customerId().isBlank()
                    && !request.customerId().startsWith("cus_mock") && !request.customerId().contains("-")) {
                payload.put("customer_settings", Map.of(
                        "customer_id", request.customerId(),
                        "editable", true
                ));
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("tenant_id", request.tenantId().toString());
            if (request.planCode() != null) {
                metadata.put("plan_code", request.planCode());
            }
            if (request.subscriptionId() != null) {
                metadata.put("subscription_id", request.subscriptionId().toString());
            }
            payload.put("metadata", metadata);

            String responseBody = getRestClient().post()
                    .uri("/paymentlinks")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String id = root.path("id").asText();
            String url = root.path("url").asText();
            String status = root.path("status").asText("active");

            log.info("Pagar.me Hosted Checkout criado com sucesso no Pagar.me: ID={}, URL={}", id, url);

            return PagarmeDto.PaymentLinkResponse.builder()
                    .id(id)
                    .url(url)
                    .status(status)
                    .type("subscription")
                    .planId(request.pagarmePlanId())
                    .createdAt(OffsetDateTime.now())
                    .build();

        } catch (Exception ex) {
            log.error("Erro ao chamar API do Pagar.me para criar Payment Link: {}", ex.getMessage(), ex);
            throw new IllegalStateException("Falha ao gerar checkout hospedado na Pagar.me: " + ex.getMessage(), ex);
        }
    }

    @Override
    public PagarmeDto.CustomerResponse createOrGetCustomer(PagarmeDto.CustomerRequest request) {
        log.info("Criando/Obtendo Customer no Pagar.me para email: {}", request.email());

        if (properties.isMockEnabled()) {
            return PagarmeDto.CustomerResponse.builder()
                    .id("cus_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                    .name(request.name())
                    .email(request.email())
                    .document(request.document())
                    .type(request.type() != null ? request.type() : "company")
                    .build();
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", request.name());
            payload.put("email", request.email());
            if (request.document() != null && !request.document().isBlank()) {
                payload.put("document", request.document().replaceAll("\\D", ""));
                payload.put("type", request.document().replaceAll("\\D", "").length() > 11 ? "company" : "individual");
            }
            if (request.phone() != null && !request.phone().isBlank()) {
                String rawPhone = request.phone().replaceAll("\\D", "");
                if (rawPhone.length() >= 10) {
                    payload.put("phones", Map.of(
                            "mobile_phone", Map.of(
                                    "country_code", "55",
                                    "area_code", rawPhone.substring(0, 2),
                                    "number", rawPhone.substring(2)
                            )
                    ));
                }
            }

            String responseBody = getRestClient().post()
                    .uri("/customers")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String id = root.path("id").asText();
            log.info("Customer criado/obtido com sucesso no Pagar.me: {}", id);

            return PagarmeDto.CustomerResponse.builder()
                    .id(id)
                    .name(root.path("name").asText())
                    .email(root.path("email").asText())
                    .document(root.path("document").asText())
                    .type(root.path("type").asText())
                    .build();

        } catch (Exception ex) {
            log.warn("Não foi possível criar Customer prévio no Pagar.me (o Hosted Checkout coletará os dados diretamente): {}", ex.getMessage());
            return null;
        }
    }

    @Override
    public List<PagarmeDto.PagarmePlanDto> listPlans() {
        if (properties.isMockEnabled()) {
            return getDefaultMockPlans();
        }

        try {
            String responseBody = getRestClient().get()
                    .uri("/plans?page=1&size=50")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode dataArray = root.has("data") ? root.get("data") : root;

            List<PagarmeDto.PagarmePlanDto> plans = new ArrayList<>();
            if (dataArray.isArray()) {
                for (JsonNode node : dataArray) {
                    String id = node.path("id").asText();
                    String name = node.path("name").asText();
                    String status = node.path("status").asText();
                    String interval = node.path("interval").asText();
                    int intervalCount = node.path("interval_count").asInt(1);
                    String billingType = node.path("billing_type").asText("prepaid");

                    int priceCents = 0;
                    JsonNode items = node.path("items");
                    if (items.isArray() && !items.isEmpty()) {
                        priceCents = items.get(0).path("pricing_scheme").path("price").asInt(0);
                    }

                    plans.add(PagarmeDto.PagarmePlanDto.builder()
                            .id(id)
                            .name(name)
                            .status(status)
                            .interval(interval)
                            .intervalCount(intervalCount)
                            .billingType(billingType)
                            .priceInCents(priceCents)
                            .price(BigDecimal.valueOf(priceCents).divide(BigDecimal.valueOf(100)))
                            .build());
                }
            }

            return plans.isEmpty() ? getDefaultMockPlans() : plans;

        } catch (Exception ex) {
            log.warn("Erro ao buscar catálogo de planos no Pagar.me: {}. Usando catálogo padrão.", ex.getMessage());
            return getDefaultMockPlans();
        }
    }

    @Override
    public PagarmeDto.PagarmePlanDto getPlan(String planId) {
        if (properties.isMockEnabled() || planId == null || planId.isBlank()) {
            return getDefaultMockPlans().stream()
                    .filter(p -> p.id().equals(planId))
                    .findFirst()
                    .orElseGet(() -> getDefaultMockPlans().get(1));
        }

        try {
            String responseBody = getRestClient().get()
                    .uri("/plans/" + planId)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(responseBody);
            int priceCents = 0;
            JsonNode items = node.path("items");
            if (items.isArray() && !items.isEmpty()) {
                priceCents = items.get(0).path("pricing_scheme").path("price").asInt(0);
            }

            return PagarmeDto.PagarmePlanDto.builder()
                    .id(node.path("id").asText())
                    .name(node.path("name").asText())
                    .status(node.path("status").asText())
                    .interval(node.path("interval").asText())
                    .intervalCount(node.path("interval_count").asInt(1))
                    .billingType(node.path("billing_type").asText())
                    .priceInCents(priceCents)
                    .price(BigDecimal.valueOf(priceCents).divide(BigDecimal.valueOf(100)))
                    .build();

        } catch (Exception ex) {
            log.warn("Erro ao obter plano {} no Pagar.me: {}", planId, ex.getMessage());
            return getDefaultMockPlans().get(1);
        }
    }

    @Override
    public boolean cancelSubscription(String gatewaySubscriptionId) {
        if (gatewaySubscriptionId == null || gatewaySubscriptionId.isBlank()) {
            return true;
        }

        if (properties.isMockEnabled()) {
            log.info("[MOCK] Assinatura cancelada com sucesso no gateway: {}", gatewaySubscriptionId);
            return true;
        }

        try {
            getRestClient().delete()
                    .uri("/subscriptions/" + gatewaySubscriptionId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Assinatura {} cancelada com sucesso no Pagar.me", gatewaySubscriptionId);
            return true;
        } catch (Exception ex) {
            log.error("Erro ao cancelar assinatura {} no Pagar.me: {}", gatewaySubscriptionId, ex.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        if (properties.getWebhookSecret() == null || properties.getWebhookSecret().isBlank()
                || properties.getWebhookSecret().contains("placeholder")) {
            return true; // Se em teste sem chave secreta estrita
        }

        if (signature == null || signature.isBlank()) {
            return false;
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(properties.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hash);

            String cleanSignature = signature.startsWith("sha256=") ? signature.substring(7) : signature;
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), cleanSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Erro ao validar assinatura do Webhook Pagar.me: {}", e.getMessage());
            return false;
        }
    }

    private PagarmeDto.PaymentLinkResponse generateMockHostedCheckout(PagarmeDto.CreateHostedCheckoutRequest request) {
        String paymentLinkId = "pl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String checkoutUrl = (request.successUrl() != null && !request.successUrl().isBlank())
                ? request.successUrl()
                : "https://checkout.pagar.me/" + paymentLinkId;
        log.info("[TEST-FALLBACK] Checkout Hospedado gerado: ID={}, URL={}", paymentLinkId, checkoutUrl);

        return PagarmeDto.PaymentLinkResponse.builder()
                .id(paymentLinkId)
                .url(checkoutUrl)
                .status("active")
                .type("subscription")
                .planId(request.pagarmePlanId())
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private List<PagarmeDto.PagarmePlanDto> getDefaultMockPlans() {
        return List.of(
                PagarmeDto.PagarmePlanDto.builder()
                        .id("plan_QjP9YkMU7KHxomNz")
                        .name("GoMech Starter")
                        .status("active")
                        .interval("month")
                        .intervalCount(1)
                        .billingType("prepaid")
                        .priceInCents(9900)
                        .price(BigDecimal.valueOf(99.00))
                        .paymentMethods(List.of("credit_card", "boleto"))
                        .build(),
                PagarmeDto.PagarmePlanDto.builder()
                        .id("plan_veoYEdYhdxU9qJ9X")
                        .name("GoMech Pro")
                        .status("active")
                        .interval("month")
                        .intervalCount(1)
                        .billingType("prepaid")
                        .priceInCents(19900)
                        .price(BigDecimal.valueOf(199.00))
                        .paymentMethods(List.of("credit_card", "boleto"))
                        .build(),
                PagarmeDto.PagarmePlanDto.builder()
                        .id("plan_2jVwBLXIVEiKR3xq")
                        .name("GoMech Enterprise")
                        .status("active")
                        .interval("month")
                        .intervalCount(1)
                        .billingType("prepaid")
                        .priceInCents(39900)
                        .price(BigDecimal.valueOf(399.00))
                        .paymentMethods(List.of("credit_card", "boleto"))
                        .build()
        );
    }
}
