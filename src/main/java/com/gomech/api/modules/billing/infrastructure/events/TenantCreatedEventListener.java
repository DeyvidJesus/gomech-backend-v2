package com.gomech.api.modules.billing.infrastructure.events;

import com.gomech.api.modules.billing.application.SubscriptionService;
import com.gomech.api.modules.iam.events.TenantCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCreatedEventListener {

    private final SubscriptionService subscriptionService;

    @EventListener
    public void onTenantCreated(TenantCreatedEvent event) {
        if (event == null || event.tenantId() == null) {
            return;
        }
        log.info("Evento TenantCreatedEvent recebido pelo módulo de Billing para o tenant: {}", event.tenantId());
        try {
            subscriptionService.createDefaultTrialSubscription(event.tenantId());
        } catch (Exception ex) {
            log.error("Erro ao provisionar assinatura inicial de teste para o tenant {}: {}", event.tenantId(), ex.getMessage(), ex);
        }
    }
}
