package com.gomech.api.modules.finance.application;

import com.gomech.api.modules.finance.api.dto.AccountDtos;
import com.gomech.api.modules.finance.domain.AccountNotFoundException;
import com.gomech.api.modules.finance.infrastructure.persistence.entity.FinanceAccount;
import com.gomech.api.modules.finance.infrastructure.persistence.repository.FinanceAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FinanceAccountService {

    private final FinanceAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<AccountDtos.Response> listAccounts(UUID tenantId, UUID unitId) {
        List<FinanceAccount> list = (unitId != null)
                ? accountRepository.findAllByTenantIdAndUnitId(tenantId, unitId)
                : accountRepository.findAllByTenantId(tenantId);

        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountDtos.Response getAccount(UUID id, UUID tenantId) {
        FinanceAccount account = accountRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return mapToResponse(account);
    }

    @Transactional
    public AccountDtos.Response createAccount(AccountDtos.Create request, UUID tenantId) {
        BigDecimal initialBal = request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO;

        FinanceAccount account = FinanceAccount.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(request.unitId())
                .name(request.name().trim())
                .type(request.type())
                .bankName(request.bankName() != null ? request.bankName().trim() : null)
                .accountNumber(request.accountNumber() != null ? request.accountNumber().trim() : null)
                .agency(request.agency() != null ? request.agency().trim() : null)
                .initialBalance(initialBal)
                .currentBalance(initialBal)
                .isActive(true)
                .build();

        account = accountRepository.save(account);
        log.info("Created finance account {} for tenant {}", account.getName(), tenantId);
        return mapToResponse(account);
    }

    @Transactional
    public AccountDtos.Response updateAccount(UUID id, AccountDtos.Update request, UUID tenantId) {
        FinanceAccount account = accountRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new AccountNotFoundException(id));

        account.setName(request.name().trim());
        if (request.bankName() != null) account.setBankName(request.bankName().trim());
        if (request.accountNumber() != null) account.setAccountNumber(request.accountNumber().trim());
        if (request.agency() != null) account.setAgency(request.agency().trim());
        if (request.isActive() != null) account.setIsActive(request.isActive());

        account = accountRepository.save(account);
        return mapToResponse(account);
    }

    @Transactional
    public void creditBalance(UUID accountId, BigDecimal amount, UUID tenantId) {
        FinanceAccount account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.setCurrentBalance(account.getCurrentBalance().add(amount));
        accountRepository.save(account);
    }

    @Transactional
    public void debitBalance(UUID accountId, BigDecimal amount, UUID tenantId) {
        FinanceAccount account = accountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
        account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
        accountRepository.save(account);
    }

    private AccountDtos.Response mapToResponse(FinanceAccount a) {
        return AccountDtos.Response.builder()
                .id(a.getId())
                .tenantId(a.getTenantId())
                .unitId(a.getUnitId())
                .name(a.getName())
                .type(a.getType())
                .bankName(a.getBankName())
                .accountNumber(a.getAccountNumber())
                .agency(a.getAgency())
                .initialBalance(a.getInitialBalance())
                .currentBalance(a.getCurrentBalance())
                .isActive(a.getIsActive())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }
}
