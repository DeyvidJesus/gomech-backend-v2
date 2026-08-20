package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.FinanceContract;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinancePayableRepository;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceReceivableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FinanceContractImpl implements FinanceContract {

    private final FinanceReceivableRepository receivableRepository;
    private final FinancePayableRepository payableRepository;

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getPendingReceivablesTotal(UUID tenantId) {
        return receivableRepository.sumPendingReceivables(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getPendingPayablesTotal(UUID tenantId) {
        return payableRepository.sumPendingPayables(tenantId);
    }
}
