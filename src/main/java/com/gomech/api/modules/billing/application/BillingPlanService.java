package com.gomech.api.modules.billing.application;

import com.gomech.api.modules.billing.api.dto.BillingPlanResponse;
import com.gomech.api.modules.billing.api.dto.PlanFeatureDto;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlan;
import com.gomech.api.modules.billing.infrastructure.persistence.model.BillingPlanFeature;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.BillingPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingPlanService {

    private final BillingPlanRepository billingPlanRepository;

    @Transactional(readOnly = true)
    public List<BillingPlanResponse> getAvailablePlans() {
        return billingPlanRepository.findAllByActiveTrue().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<BillingPlan> findByCode(String code) {
        return billingPlanRepository.findByCode(code);
    }

    public BillingPlanResponse toResponse(BillingPlan plan) {
        List<PlanFeatureDto> features = plan.getFeatures() != null
                ? plan.getFeatures().stream().map(this::toFeatureDto).toList()
                : List.of();

        return new BillingPlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getPrice(),
                plan.getBillingInterval(),
                plan.isActive(),
                features
        );
    }

    public PlanFeatureDto toFeatureDto(BillingPlanFeature feature) {
        return new PlanFeatureDto(
                feature.getFeatureCode(),
                feature.getLimitValue(),
                feature.isEnabled(),
                feature.getUnitOfMeasure()
        );
    }
}
