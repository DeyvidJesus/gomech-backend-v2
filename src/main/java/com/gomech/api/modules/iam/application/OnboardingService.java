package com.gomech.api.modules.iam.application;

import com.gomech.api.core.entitlement.application.EntitlementService;
import com.gomech.api.core.entitlement.domain.QuotaDimension;
import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.security.JwtUtil;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.iam.api.dto.UserSummaryDto;
import com.gomech.api.modules.iam.events.TenantCreatedEvent;
import com.gomech.api.modules.iam.infrastructure.persistence.model.*;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.TenantRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UnitRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final TenantRepository tenantRepository;
    private final UnitRepository unitRepository;
    private final RoleService roleService;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final DomainEventBus domainEventBus;
    private final EntitlementService entitlementService;

    @Value("${jwt.expiration:900000}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long jwtRefreshExpiration;

    @Transactional
    public AuthResponse register(RegisterWorkshopRequest request, UUID newTenantId) {
        return registerWithTenantId(request, newTenantId, null, null, null);
    }

    @Transactional
    public AuthResponse registerWorkshop(RegisterWorkshopRequest request, String ipAddress, String userAgent, String deviceInfo) {
        UUID newTenantId = UUID.randomUUID();
        return registerWithTenantId(request, newTenantId, ipAddress, userAgent, deviceInfo);
    }

    @Transactional
    public AuthResponse registerWithTenantId(RegisterWorkshopRequest request, UUID newTenantId, String ipAddress, String userAgent, String deviceInfo) {
        // Estabelece o Tenant no contexto para viabilizar as inserções subsequentes
        TenantContextHolder.setTenantId(newTenantId);

        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }

        // 1. Criar Tenant
        Tenant tenant = new Tenant();
        tenant.setId(newTenantId);
        tenant.setName(request.workshopName());
        // Mocking CNPJ provisório se não informado
        tenant.setCnpj("00.000.000/" + newTenantId.toString().substring(0, 4) + "-00");
        tenant = tenantRepository.save(tenant);

        // Publicar evento de domínio de criação de Tenant para Billing e outros módulos
        domainEventBus.publish(new TenantCreatedEvent(newTenantId, request.workshopName(), request.email()));

        // 2. Criar Unidade Matriz
        Unit unit = new Unit();
        unit.setTenantId(newTenantId);
        unit.setName("Matriz");
        unit.setAddress(request.address());
        unit.setHeadquarters(true);
        unit = unitRepository.save(unit);

        // 3. Provisionar catálogo de papéis padrão (Proprietário, Gerente, Mecânico, Atendente)
        Role ownerRole = roleService.provisionDefaultRoles(newTenantId);

        // 4. Criar Usuário Proprietário
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName(request.ownerName());
        user.setEmail(request.email().toLowerCase().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setLastLogin(OffsetDateTime.now());
        user = userRepository.save(user);

        // 5. Vincular Papel de Proprietário ao Usuário na Unidade Matriz
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(ownerRole);
        userRole.setUnit(unit);
        userRole.setTenantId(tenant.getId());
        user.getUserRoles().add(userRole);
        userRepository.save(user);

        // Registrar consumo inicial de cota para a Unidade Matriz e o Usuário Proprietário
        entitlementService.recordUsage(newTenantId, QuotaDimension.UNITS, 1L);
        entitlementService.recordUsage(newTenantId, QuotaDimension.USERS, 1L);

        // 6. Gerar Tokens proprietários GoMech
        List<String> roles = List.of(ownerRole.getName());
        List<String> permissions = ownerRole.getPermissions().stream().map(Permission::getCode).toList();
        String accessToken = jwtUtil.generateToken(user.getId(), user.getTenantId(), unit.getId(), roles, permissions);
        String refreshToken = UUID.randomUUID().toString();

        UserSession session = new UserSession();
        session.setUser(user);
        session.setTenantId(tenant.getId());
        session.setFamilyId(UUID.randomUUID());
        session.setRefreshToken(refreshToken);
        session.setExpiresAt(OffsetDateTime.now().plusSeconds(jwtRefreshExpiration / 1000));
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.setDeviceInfo(deviceInfo);
        session.setLastUsedAt(OffsetDateTime.now());
        session.setRevoked(false);
        userSessionRepository.save(session);

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                tenant.getId(),
                unit.getId(),
                roles,
                permissions
        );

        return new AuthResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtExpiration / 1000,
                userSummary
        );
    }
}
