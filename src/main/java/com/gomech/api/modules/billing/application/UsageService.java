package com.gomech.api.modules.billing.application;

import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.modules.billing.api.dto.UsageRecordResponse;
import com.gomech.api.modules.billing.infrastructure.persistence.model.UsageRecord;
import com.gomech.api.modules.billing.infrastructure.persistence.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UsageService {

    private final UsageRecordRepository usageRecordRepository;

    @Transactional
    public void recordUsage(UUID tenantId, UUID unitId, QuotaDimension dimension, long amount) {
        if (tenantId == null || dimension == null || amount <= 0) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime periodStart = now.with(TemporalAdjusters.firstDayOfMonth()).withHour(0).withMinute(0).withSecond(0).withNano(0);
        OffsetDateTime periodEnd = periodStart.plusMonths(1);

        UsageRecord record = usageRecordRepository.findCurrentPeriodUsage(tenantId, dimension.name(), now)
                .orElseGet(() -> {
                    UsageRecord newRecord = new UsageRecord();
                    newRecord.setTenantId(tenantId);
                    newRecord.setUnitId(unitId);
                    newRecord.setDimension(dimension.name());
                    newRecord.setPeriodStart(periodStart);
                    newRecord.setPeriodEnd(periodEnd);
                    newRecord.setAmount(0L);
                    return newRecord;
                });

        record.setAmount(record.getAmount() + amount);
        usageRecordRepository.save(record);
        log.debug("Consumo registrado para tenant {} na dimensão {}: +{} (total: {})",
                tenantId, dimension.name(), amount, record.getAmount());
    }

    @Transactional(readOnly = true)
    public long getCurrentUsage(UUID tenantId, QuotaDimension dimension) {
        if (tenantId == null || dimension == null) {
            return 0L;
        }
        OffsetDateTime now = OffsetDateTime.now();
        return usageRecordRepository.findCurrentPeriodUsage(tenantId, dimension.name(), now)
                .map(UsageRecord::getAmount)
                .orElse(0L);
    }

    @Transactional(readOnly = true)
    public List<UsageRecordResponse> getCurrentPeriodUsageList(UUID tenantId) {
        OffsetDateTime now = OffsetDateTime.now();
        return usageRecordRepository.findAllCurrentPeriodUsage(tenantId, now).stream()
                .map(u -> new UsageRecordResponse(
                        u.getId(),
                        u.getTenantId(),
                        u.getUnitId(),
                        u.getDimension(),
                        u.getAmount(),
                        -1L, // resolved by caller if limit is required
                        u.getPeriodStart(),
                        u.getPeriodEnd()
                ))
                .toList();
    }
}
