package com.gomech.api.modules.finance.api;

import java.math.BigDecimal;
import java.util.UUID;

public interface FinanceContract {

    BigDecimal getPendingReceivablesTotal(UUID tenantId);

    BigDecimal getPendingPayablesTotal(UUID tenantId);
}
