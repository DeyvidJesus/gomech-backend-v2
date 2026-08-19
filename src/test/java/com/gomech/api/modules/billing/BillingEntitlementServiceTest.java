package com.gomech.api.modules.billing;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.entitlement.api.EntitlementDecision;
import com.gomech.api.core.entitlement.api.EntitlementSnapshot;
import com.gomech.api.core.entitlement.api.QuotaDecision;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.modules.billing.application.BillingEntitlementService;
import com.gomech.api.modules.billing.application.SubscriptionService;
import com.gomech.api.modules.billing.application.UsageService;
import com.gomech.api.modules.billing.domain.PlanCode;
import com.gomech.api.modules.billing.domain.SubscriptionStatus;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlanFeature;
import com.gomech.api.modules.billing.infrastructure.persistence.model.Subscription;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingEntitlementServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private UsageService usageService;

    @InjectMocks
    private BillingEntitlementService billingEntitlementService;

    private UUID tenantId;
    private BillingPlan starterPlan;
    private Subscription starterSubscription;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();

        starterPlan = new BillingPlan();
        starterPlan.setCode(PlanCode.STARTER);
        starterPlan.setName("Starter");

        BillingPlanFeature usersFeature = new BillingPlanFeature();
        usersFeature.setFeatureCode("USERS");
        usersFeature.setLimitValue(3L);
        usersFeature.setEnabled(true);

        BillingPlanFeature unitsFeature = new BillingPlanFeature();
        unitsFeature.setFeatureCode("UNITS");
        unitsFeature.setLimitValue(1L);
        unitsFeature.setEnabled(true);

        BillingPlanFeature aiFeature = new BillingPlanFeature();
        aiFeature.setFeatureCode("AI_USAGE");
        aiFeature.setLimitValue(500L);
        aiFeature.setEnabled(true);

        BillingPlanFeature crmFeature = new BillingPlanFeature();
        crmFeature.setFeatureCode("MODULE_CRM");
        crmFeature.setLimitValue(-1L);
        crmFeature.setEnabled(true);

        BillingPlanFeature financeFeature = new BillingPlanFeature();
        financeFeature.setFeatureCode("MODULE_FINANCE");
        financeFeature.setLimitValue(0L);
        financeFeature.setEnabled(false);

        starterPlan.setFeatures(List.of(usersFeature, unitsFeature, aiFeature, crmFeature, financeFeature));

        starterSubscription = new Subscription();
        starterSubscription.setTenantId(tenantId);
        starterSubscription.setPlan(starterPlan);
        starterSubscription.setPlanCode(PlanCode.STARTER);
        starterSubscription.setPlanName("Starter");
        starterSubscription.setStatus(SubscriptionStatus.ACTIVE.name());
    }

    @Test
    @DisplayName("Deve resolver snapshot intersectando permissões com módulos habilitados no plano")
    void shouldResolveSnapshotIntersectingPermissionsWithPlanModules() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(starterSubscription));

        ActorContext actor = new ActorContext(
                UUID.randomUUID(),
                tenantId,
                new com.gomech.api.core.tenancy.UnitReference(UUID.randomUUID()),
                Set.of("Proprietário"),
                Set.of("CRM_CUSTOMER_READ", "FINANCE_TRANSACTION_READ", "OPERATIONS_WORK_ORDER_READ")
        );

        EntitlementSnapshot snapshot = billingEntitlementService.resolve(actor);

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.planCode()).isEqualTo(PlanCode.STARTER);
        assertThat(snapshot.enabledModules()).contains("CRM");
        assertThat(snapshot.enabledModules()).doesNotContain("FINANCE");
        // Permissão de CRM é permitida, Finance é withheld porque MODULE_FINANCE está desabilitado
        assertThat(snapshot.permissions()).contains("CRM_CUSTOMER_READ");
        assertThat(snapshot.permissions()).doesNotContain("FINANCE_TRANSACTION_READ");
    }

    @Test
    @DisplayName("Deve permitir acesso a módulo incluído no plano")
    void shouldAllowAccessToIncludedModule() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(starterSubscription));

        EntitlementDecision decision = billingEntitlementService.checkModuleAccess(tenantId, "CRM");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).contains("module_included_in_plan");
    }

    @Test
    @DisplayName("Deve negar acesso a módulo não incluído no plano")
    void shouldDenyAccessToExcludedModule() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(starterSubscription));

        EntitlementDecision decision = billingEntitlementService.checkModuleAccess(tenantId, "FINANCE");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("module_not_included_in_plan");
    }

    @Test
    @DisplayName("Deve permitir cota quando consumo somado estiver dentro do limite")
    void shouldAllowQuotaWhenUsageWithinLimit() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(starterSubscription));
        when(usageService.getCurrentUsage(tenantId, QuotaDimension.AI_USAGE)).thenReturn(400L);

        QuotaDecision decision = billingEntitlementService.checkQuota(tenantId, QuotaDimension.AI_USAGE, 50L);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.limit()).isEqualTo(500L);
        assertThat(decision.currentUsage()).isEqualTo(400L);
    }

    @Test
    @DisplayName("Deve negar cota quando incremento ultrapassar o limite do plano")
    void shouldDenyQuotaWhenUsageExceedsLimit() {
        when(subscriptionRepository.findByTenantId(tenantId)).thenReturn(Optional.of(starterSubscription));
        when(usageService.getCurrentUsage(tenantId, QuotaDimension.AI_USAGE)).thenReturn(480L);

        QuotaDecision decision = billingEntitlementService.checkQuota(tenantId, QuotaDimension.AI_USAGE, 50L);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(500L);
        assertThat(decision.currentUsage()).isEqualTo(480L);
        assertThat(decision.reason()).contains("quota_exceeded");
    }

    @Test
    @DisplayName("Deve registrar consumo chamando UsageService")
    void shouldRecordUsageDelegatingToUsageService() {
        billingEntitlementService.recordUsage(tenantId, QuotaDimension.AI_USAGE, 10L);
        verify(usageService).recordUsage(eq(tenantId), isNull(), eq(QuotaDimension.AI_USAGE), eq(10L));
    }
}
