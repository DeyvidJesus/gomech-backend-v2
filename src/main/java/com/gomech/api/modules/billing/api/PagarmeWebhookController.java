package com.gomech.api.modules.billing.api;

import com.gomech.api.modules.billing.application.WebhookProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/webhooks/pagarme")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Billing - Webhooks", description = "Recepção e processamento idempotente de notificações Pagar.me")
public class PagarmeWebhookController {

    private final WebhookProcessingService webhookService;

    @PostMapping
    @Operation(summary = "Receber evento de ciclo de vida de pagamento/assinatura do Pagar.me")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Hub-Signature", required = false) String hubSignature,
            @RequestHeader(value = "X-Pagarme-Signature", required = false) String pagarmeSignature,
            @RequestHeader(value = "Signature", required = false) String signatureHeader
    ) {
        String effectiveSignature = pagarmeSignature != null ? pagarmeSignature : (hubSignature != null ? hubSignature : signatureHeader);

        try {
            boolean processed = webhookService.processWebhook(rawPayload, effectiveSignature);
            return ResponseEntity.ok(Map.of("status", "received", "processed", processed));
        } catch (SecurityException se) {
            log.warn("Rejeitando webhook com assinatura inválida: {}", se.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", se.getMessage()));
        } catch (Exception e) {
            log.error("Erro inesperado ao processar webhook Pagar.me: {}", e.getMessage(), e);
            // Retorna 200/400 conforme política de retry do Pagar.me
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        }
    }
}
