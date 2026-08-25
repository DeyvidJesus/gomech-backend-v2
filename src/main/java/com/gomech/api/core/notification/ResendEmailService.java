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
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ResendEmailService {

    @Value("${resend.api-key:re_mock_api_key_gomech_test}")
    private String resendApiKey;

    @Value("${resend.from-email:GoMech ERP <nao-responda@gomech.com.br>}")
    private String fromEmail;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Envia e-mail genérico via Resend API
     */
    public boolean sendEmail(String toEmail, String subject, String htmlContent) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Tentativa de envio de e-mail com destinatário vazio");
            return false;
        }

        try {
            log.info("Disparando e-mail via Resend para '{}' com assunto '{}'", toEmail, subject);

            // Se for chave mock, apenas registra em log com sucesso
            if (resendApiKey == null || resendApiKey.startsWith("re_mock") || resendApiKey.isBlank()) {
                log.info("[MOCK EMAIL RESEND] Para: {} | Assunto: {} | De: {}", toEmail, subject, fromEmail);
                return true;
            }

            Map<String, Object> payload = Map.of(
                    "from", fromEmail,
                    "to", List.of(toEmail),
                    "subject", subject,
                    "html", htmlContent
            );

            String requestBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("E-mail enviado com sucesso via Resend para {}: Status {}", toEmail, response.statusCode());
                return true;
            } else {
                log.error("Falha ao enviar e-mail via Resend para {}: Status {} - {}", toEmail, response.statusCode(), response.body());
                return false;
            }
        } catch (Exception ex) {
            log.error("Erro inesperado ao enviar e-mail via Resend para {}: {}", toEmail, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * 1. Boas-vindas e Ativação de Colaborador
     */
    public boolean sendWelcomeUserEmail(String toEmail, String userName, String workshopName, String temporaryPassword) {
        String subject = "Bem-vindo ao GoMech ERP - Acesso à " + workshopName;
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1e3a8a; padding: 24px; text-align: center; color: #ffffff;">
                        <h1 style="margin: 0; font-size: 22px;">GoMech ERP</h1>
                        <p style="margin: 4px 0 0; font-size: 14px; opacity: 0.9;">Plataforma de Gestão Automotiva</p>
                    </div>
                    <div style="padding: 28px; color: #1e293b;">
                        <h2 style="font-size: 18px; margin-top: 0;">Olá, %s!</h2>
                        <p style="line-height: 1.6;">Você foi cadastrado na equipe de <strong>%s</strong> no GoMech ERP.</p>
                        <div style="background: #f8fafc; border: 1px solid #cbd5e1; border-radius: 8px; padding: 16px; margin: 20px 0;">
                            <p style="margin: 0 0 8px; font-size: 13px; color: #64748b;">Suas credenciais de acesso:</p>
                            <p style="margin: 0; font-size: 14px;"><strong>E-mail:</strong> %s</p>
                            <p style="margin: 4px 0 0; font-size: 14px;"><strong>Senha Inicial:</strong> <code style="background: #e2e8f0; padding: 2px 6px; border-radius: 4px;">%s</code></p>
                        </div>
                        <p style="text-align: center; margin: 28px 0;">
                            <a href="https://gomech-frontend-7217905842.us-central1.run.app/login" style="background: #2563eb; color: #ffffff; text-decoration: none; padding: 12px 28px; border-radius: 8px; font-weight: bold; font-size: 14px; display: inline-block;">Acessar Sistema</a>
                        </p>
                        <p style="font-size: 12px; color: #94a3b8; line-height: 1.4;">Por segurança, recomendamos que você altere sua senha logo após o primeiro login em seu perfil.</p>
                    </div>
                </div>
                """.formatted(userName, workshopName, toEmail, temporaryPassword);

        return sendEmail(toEmail, subject, html);
    }

    /**
     * 2. Notificação de Atualização de Papel / Permissões
     */
    public boolean sendRoleUpdateNotification(String toEmail, String userName, String newRoleName) {
        String subject = "Atualização de Permissões no GoMech ERP";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;">
                    <div style="background: #0f172a; padding: 20px; text-align: center; color: #ffffff;">
                        <h2 style="margin: 0; font-size: 20px;">GoMech ERP</h2>
                    </div>
                    <div style="padding: 24px; color: #1e293b;">
                        <h3 style="margin-top: 0;">Permissões Atualizadas</h3>
                        <p>Olá, <strong>%s</strong>,</p>
                        <p>Seu perfil de acesso foi atualizado pela gerência para o cargo de: <strong style="color: #2563eb;">%s</strong>.</p>
                        <p>As novas permissões já estão disponíveis para uso imediato em sua conta.</p>
                    </div>
                </div>
                """.formatted(userName, newRoleName);

        return sendEmail(toEmail, subject, html);
    }

    /**
     * 3. Notificação de Atribuição de Tarefa em Equipe
     */
    public boolean sendTaskAssignmentNotification(String toEmail, String userName, String taskType, String taskDescription, String accessLink) {
        String subject = "Nova Atribuição de Tarefa: " + taskType;
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;">
                    <div style="background: #2563eb; padding: 20px; text-align: center; color: #ffffff;">
                        <h2 style="margin: 0; font-size: 20px;">Nova Atribuição no GoMech</h2>
                    </div>
                    <div style="padding: 24px; color: #1e293b;">
                        <p>Olá, <strong>%s</strong>,</p>
                        <p>Você foi designado como responsável por uma nova tarefa:</p>
                        <div style="background: #eff6ff; border-left: 4px solid #2563eb; padding: 14px; margin: 16px 0;">
                            <p style="margin: 0; font-weight: bold; color: #1e40af;">%s</p>
                            <p style="margin: 6px 0 0; font-size: 14px;">%s</p>
                        </div>
                        <p style="text-align: center; margin-top: 24px;">
                            <a href="%s" style="background: #2563eb; color: #ffffff; text-decoration: none; padding: 10px 24px; border-radius: 8px; font-weight: bold; font-size: 13px; display: inline-block;">Ver Detalhes</a>
                        </p>
                    </div>
                </div>
                """.formatted(userName, taskType, taskDescription, accessLink);

        return sendEmail(toEmail, subject, html);
    }

    /**
     * 4. Envio de Orçamento para o Cliente
     */
    public boolean sendQuoteToCustomer(String customerEmail, String customerName, String workshopName, String quoteCode, BigDecimal totalAmount, String portalUrl) {
        String subject = "Orçamento #" + quoteCode + " - " + workshopName;
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; background: #ffffff; border: 1px solid #e2e8f0; border-radius: 12px; overflow: hidden;">
                    <div style="background: #1e3a8a; padding: 24px; text-align: center; color: #ffffff;">
                        <h1 style="margin: 0; font-size: 22px;">%s</h1>
                        <p style="margin: 4px 0 0; font-size: 13px; opacity: 0.9;">Orçamento de Serviços e Peças</p>
                    </div>
                    <div style="padding: 28px; color: #1e293b;">
                        <h2 style="font-size: 18px; margin-top: 0;">Olá, %s!</h2>
                        <p>A oficina <strong>%s</strong> finalizou a análise técnica e preparou o seu orçamento com valor total de <strong style="color: #059669; font-size: 16px;">R$ %.2f</strong>.</p>
                        <p>Você pode conferir todos os itens detalhados e aprovar diretamente pelo nosso portal online:</p>
                        <p style="text-align: center; margin: 28px 0;">
                            <a href="%s" style="background: #059669; color: #ffffff; text-decoration: none; padding: 12px 28px; border-radius: 8px; font-weight: bold; font-size: 14px; display: inline-block;">Aprovar Orçamento Online</a>
                        </p>
                        <p style="font-size: 12px; color: #94a3b8;">Em caso de dúvidas, entre em contato diretamente com a nossa equipe.</p>
                    </div>
                </div>
                """.formatted(workshopName, customerName, workshopName, totalAmount, portalUrl);

        return sendEmail(customerEmail, subject, html);
    }
}
