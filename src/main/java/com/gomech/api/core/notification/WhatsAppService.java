package com.gomech.api.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class WhatsAppService {

    @Value("${whatsapp.api-url:https://api.evolution-api.com}")
    private String apiUrl;

    @Value("${whatsapp.api-key:mock-whatsapp-key}")
    private String apiKey;

    @Value("${whatsapp.instance-name:gomech-main}")
    private String instanceName;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Envia mensagem de texto via WhatsApp (Evolution API / Z-API / WhatsApp Cloud)
     */
    public boolean sendMessage(String phoneNumber, String message) {
        if (phoneNumber == null || phoneNumber.isBlank() || message == null || message.isBlank()) {
            log.warn("Tentativa de envio de WhatsApp com telefone ou mensagem vazia");
            return false;
        }

        String cleanPhone = phoneNumber.replaceAll("\\D", "");
        if (!cleanPhone.startsWith("55") && cleanPhone.length() >= 10 && cleanPhone.length() <= 11) {
            cleanPhone = "55" + cleanPhone;
        }

        try {
            log.info("Disparando WhatsApp para '{}'", cleanPhone);

            if (apiKey == null || apiKey.startsWith("mock") || apiKey.isBlank()) {
                log.info("[MOCK WHATSAPP] Para: {} | Mensagem:\n{}", cleanPhone, message);
                return true;
            }

            Map<String, Object> body = Map.of(
                    "number", cleanPhone,
                    "text", message
            );

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/message/sendText/" + instanceName))
                    .header("apikey", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception ex) {
            log.error("Erro ao enviar mensagem via WhatsApp para {}: {}", cleanPhone, ex.getMessage());
            return false;
        }
    }

    /**
     * Envia notificação de Orçamento para o cliente via WhatsApp
     */
    public boolean sendQuoteNotification(String phoneNumber, String customerName, String workshopName, String quoteCode, BigDecimal totalAmount, String portalUrl) {
        String message = String.format(
                " Olá, *%s*!\n\n" +
                "A *%s* finalizou o orçamento *#%s* para o seu veículo no valor de *R$ %.2f*.\n\n" +
                "Acesse o link abaixo para visualizar os itens detalhados e aprovar os serviços online:\n" +
                " %s\n\n" +
                "_GoMech ERP - Gestão Automotiva Inteligente_",
                customerName, workshopName, quoteCode, totalAmount, portalUrl
        );

        return sendMessage(phoneNumber, message);
    }
}
