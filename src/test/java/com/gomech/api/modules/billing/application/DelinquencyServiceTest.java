package com.gomech.api.modules.billing.application;

import com.gomech.api.core.events.TenantReactivatedEvent;
import com.gomech.api.core.events.TenantSuspendedEvent;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DelinquencyServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DelinquencyService delinquencyService;

    private UUID tenantId;
    private Subscription subscription;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        subscription = new Subscription();
        subscription.setTenantId(tenantId);
        subscription.setStatus(SubscriptionStatus.ACTIVE.name());
        subscription.setPlanCode("PRO");
    }

    @Test
    @DisplayName("Should mark tenant as delinquent, transition status to PAST_DUE and publish TenantSuspendedEvent")
    void shouldMarkDelinquentAndPublishSuspendedEvent() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        delinquencyService.markDelinquent(tenantId, "Cartão recusado 3x");

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE.name());
        assertThat(subscription.getDelinquentSince()).isNotNull();
        verify(eventPublisher).publishEvent(any(TenantSuspendedEvent.class));
    }

    @Test
    @DisplayName("Should recover delinquency, transition status to ACTIVE and publish TenantReactivatedEvent")
    void shouldRecoverDelinquencyAndPublishReactivatedEvent() {
        subscription.setStatus(SubscriptionStatus.PAST_DUE.name());
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(subscription));

        delinquencyService.recoverDelinquency(tenantId);

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        assertThat(subscription.getDelinquentSince()).isNull();
        verify(eventPublisher).publishEvent(any(TenantReactivatedEvent.class));
    }
}
