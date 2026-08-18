package com.gomech.api.modules.iam.application;

import com.gomech.api.core.security.JwtUtil;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.iam.infrastructure.persistence.model.*;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final TenantRepository tenantRepository;
    private final UnitRepository unitRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long jwtRefreshExpiration;

    @Transactional
    public AuthResponse register(RegisterWorkshopRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("E-mail já cadastrado");
        }

        // 1. Create Tenant
        Tenant tenant = new Tenant();
        tenant.setName(request.workshopName());
        // Mocking CNPJ for now as it wasn't in step 1 of Figma, normally we would ask for it.
        tenant.setCnpj("00.000.000/" + UUID.randomUUID().toString().substring(0, 4) + "-00");
        tenant = tenantRepository.save(tenant);

        // 2. Set Tenant Context to bypass RLS issues or properly set it on entities
        TenantContextHolder.setTenantId(tenant.getId());

        try {
            // 3. Create Headquarters Unit
            Unit unit = new Unit();
            unit.setTenantId(tenant.getId());
            unit.setName("Matriz");
            unit.setAddress(request.address());
            unit.setHeadquarters(true);
            unit = unitRepository.save(unit);

            // 4. Ensure "Proprietário" role exists or create it
            Role ownerRole = roleRepository.findByName("Proprietário").orElseGet(() -> {
                Role newRole = new Role();
                newRole.setName("Proprietário");
                newRole.setDescription("Acesso total");
                return roleRepository.save(newRole);
            });

            // 5. Create Owner User
            User user = new User();
            user.setTenantId(tenant.getId());
            user.setName(request.ownerName());
            user.setEmail(request.email());
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setLastLogin(OffsetDateTime.now());
            user = userRepository.save(user);

            // 6. Assign Role to User
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(ownerRole);
            userRole.setUnit(unit);
            userRole.setTenantId(tenant.getId());
            user.getUserRoles().add(userRole);
            userRepository.save(user);

            // 7. Generate Tokens
            String accessToken = jwtUtil.generateToken(user.getId(), user.getTenantId());
            String refreshToken = UUID.randomUUID().toString();

            UserSession session = new UserSession();
            session.setUser(user);
            session.setRefreshToken(refreshToken);
            session.setExpiresAt(OffsetDateTime.now().plusSeconds(jwtRefreshExpiration / 1000));
            userSessionRepository.save(session);

            return new AuthResponse(accessToken, refreshToken, jwtExpiration / 1000);
        } finally {
            TenantContextHolder.clear();
        }
    }
}
