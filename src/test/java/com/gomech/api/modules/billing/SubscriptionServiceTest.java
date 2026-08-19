package com.gomech.api.modules.billing;

import com.gomech.api.modules.billing.api.dto.ChangePlanRequest;
import com.gomech.api.modules.billing.api.dto.SubscriptionResponse;
import com.gomech.api.modules.billing.application.SubscriptionService;
import com.gomech.api.modules.billing.domain.PlanCode;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlanFeature;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private BillingPlanRepository billingPlanRepository;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private UUID tenantId;
    private BillingPlan trialPlan;
    private BillingPlan proPlan;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        trialPlan = new BillingPlan();
        trialPlan.setCode(PlanCode.TRIAL);
        trialPlan.setName("Trial Gratuito");
        BillingPlanFeature trialFeature = new BillingPlanFeature();
        trialFeature.setFeatureCode("USERS");
        trialFeature.setLimitValue(2L);
        trialFeature.setEnabled(true);
        trialPlan.setFeatures(List.of(trialFeature));

        proPlan = new BillingPlan();
        proPlan.setCode(PlanCode.PRO);
        proPlan.setName("Profissional");
        BillingPlanFeature proFeature = new BillingPlanFeature();
        proFeature.setFeatureCode("USERS");
        proFeature.setLimitValue(10L);
        proFeature.setEnabled(true);
        proPlan.setFeatures(List.of(proFeature));
    }

    @Test
    @DisplayName("Deve provisionar assinatura TRIAL padrão para novo tenant")
    void shouldCreateDefaultTrialSubscriptionForNewTenant() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.empty());
        when(billingPlanRepository.findByCode(PlanCode.TRIAL)).thenReturn(Optional.of(trialPlan));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> {
            Subscription s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        Subscription subscription = subscriptionService.createDefaultTrialSubscription(tenantId);

        assertThat(subscription).isNotNull();
        assertThat(subscription.getPlanCode()).isEqualTo(PlanCode.TRIAL);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.TRIALING.name());
        assertThat(subscription.getTrialEndsAt()).isNotNull();
        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    @DisplayName("Deve alterar plano de assinatura do tenant com sucesso")
    void shouldChangeSubscriptionPlanSuccessfully() {
        Subscription existing = new Subscription();
        existing.setId(UUID.randomUUID());
        existing.setTenantId(tenantId);
        existing.setPlan(trialPlan);
        existing.setPlanCode(PlanCode.TRIAL);
        existing.setStatus(SubscriptionStatus.TRIALING.name());

        when(billingPlanRepository.findByCode(PlanCode.PRO)).thenReturn(Optional.of(proPlan));
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangePlanRequest request = new ChangePlanRequest(PlanCode.PRO);
        SubscriptionResponse response = subscriptionService.changePlan(tenantId, request);

        assertThat(response).isNotNull();
        assertThat(response.planCode()).isEqualTo(PlanCode.PRO);
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE.name());
        verify(subscriptionRepository).save(existing);
    }

    @Test
    @DisplayName("Deve falhar ao tentar trocar para plano inexistente")
    void shouldFailWhenChangingToNonExistentPlan() {
        when(billingPlanRepository.findByCode("INVALID_PLAN")).thenReturn(Optional.empty());

        ChangePlanRequest request = new ChangePlanRequest("INVALID_PLAN");
        assertThatThrownBy(() -> subscriptionService.changePlan(tenantId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Plano não encontrado");
    }
}
