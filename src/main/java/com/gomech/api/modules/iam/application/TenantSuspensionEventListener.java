package com.gomech.api.modules.iam.application;

import com.gomech.api.core.events.TenantSuspendedEvent;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class TenantSuspensionEventListener {

    private final UserSessionRepository userSessionRepository;

    @Async
    @EventListener
    @Transactional
    public void onTenantSuspended(TenantSuspendedEvent event) {
        log.warn("Revogando todas as sessões ativas do tenant {} devido à suspensão por inadimplência: {}",
                event.tenantId(), event.reason());

        int revokedCount = userSessionRepository.revokeAllByTenantId(event.tenantId(), OffsetDateTime.now());
        log.info("Total de {} sessões revogadas para o tenant {}", revokedCount, event.tenantId());
    }
}
