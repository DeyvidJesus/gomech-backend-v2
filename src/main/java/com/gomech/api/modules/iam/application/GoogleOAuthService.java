package com.gomech.api.modules.iam.application;

import com.gomech.api.core.security.JwtUtil;
import com.gomech.api.core.security.OAuthStateUtil;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.GoogleAuthorizeUrlResponse;
import com.gomech.api.modules.iam.api.dto.GoogleOAuthCallbackRequest;
import com.gomech.api.modules.iam.api.dto.UserSummaryDto;
import com.gomech.api.modules.iam.domain.UserStatus;
import com.gomech.api.modules.iam.infrastructure.oauth.GoogleIdTokenPayload;
import com.gomech.api.modules.iam.infrastructure.oauth.GoogleTokenResponse;
import com.gomech.api.modules.iam.infrastructure.persistence.model.*;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;

@Slf4j
@Service
public class GoogleOAuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final OAuthStateUtil oAuthStateUtil;
    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;
    private final TenantRepository tenantRepository;
    private final UnitRepository unitRepository;
    private final RoleRepository roleRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${gomech.oauth.google.client-id}")
    private String clientId;

    @Value("${gomech.oauth.google.redirect-uri}")
    private String defaultRedirectUri;

    @Value("${gomech.oauth.google.authorization-uri:https://accounts.google.com/o/oauth2/v2/auth}")
    private String authorizationUri;

    @Value("${jwt.expiration:900000}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long jwtRefreshExpiration;

    public GoogleOAuthService(
            GoogleOAuthClient googleOAuthClient,
            OAuthStateUtil oAuthStateUtil,
            UserRepository userRepository,
            UserIdentityRepository userIdentityRepository,
            TenantRepository tenantRepository,
            UnitRepository unitRepository,
            RoleRepository roleRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil
    ) {
        this.googleOAuthClient = googleOAuthClient;
        this.oAuthStateUtil = oAuthStateUtil;
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
        this.tenantRepository = tenantRepository;
        this.unitRepository = unitRepository;
        this.roleRepository = roleRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public GoogleAuthorizeUrlResponse generateAuthorizeUrl(String customRedirectUri) {
        String effectiveRedirectUri = (customRedirectUri != null && !customRedirectUri.isBlank())
                ? customRedirectUri
                : defaultRedirectUri;

        OAuthStateUtil.GeneratedOAuthContext oauthContext = oAuthStateUtil.generateContext(effectiveRedirectUri);

        String authorizationUrl = authorizationUri + "?" +
                "client_id=" + urlEncode(clientId) +
                "&redirect_uri=" + urlEncode(effectiveRedirectUri) +
                "&response_type=code" +
                "&scope=" + urlEncode("openid email profile") +
                "&state=" + urlEncode(oauthContext.signedState()) +
                "&nonce=" + urlEncode(oauthContext.nonce()) +
                "&code_challenge=" + urlEncode(oauthContext.codeChallenge()) +
                "&code_challenge_method=S256" +
                "&access_type=offline" +
                "&prompt=select_account";

        return new GoogleAuthorizeUrlResponse(authorizationUrl, oauthContext.signedState());
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Transactional
    public AuthResponse authenticate(
            GoogleOAuthCallbackRequest request,
            String ipAddress,
            String userAgent,
            String deviceInfo
    ) {
        // 1. Validar e extrair estado e parâmetros de segurança (Anti-CSRF, Nonce, PKCE)
        OAuthStateUtil.OAuthStateData stateData = oAuthStateUtil.validateAndExtractState(request.state());

        // 2. Trocar Authorization Code pelos tokens do Google
        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeCode(
                request.code(),
                stateData.codeVerifier(),
                stateData.redirectUri()
        );

        if (tokenResponse == null || tokenResponse.idToken() == null) {
            throw new SecurityException("Resposta inválida do provedor Google OAuth");
        }

        // 3. Validar OIDC ID Token (Assinatura, Issuer, Audience, Expiração, Nonce, Email Verified)
        GoogleIdTokenPayload idToken = googleOAuthClient.verifyAndExtractIdToken(
                tokenResponse.idToken(),
                stateData.nonce()
        );

        String googleSub = idToken.sub();
        String googleEmail = idToken.email().toLowerCase().trim();

        // 4. Resolução de Identidade e Account Linking
        User user = resolveOrLinkUser(googleSub, googleEmail, idToken.name());

        if (!UserStatus.isActive(user.getStatus())) {
            throw new IllegalArgumentException("Usuário inativo ou suspenso");
        }

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);

        // 5. Emitir credenciais proprietárias GoMech (Access Token JWT + Refresh Token rotacionável)
        return issueGoMechTokens(user, ipAddress, userAgent, deviceInfo);
    }

    private User resolveOrLinkUser(String googleSub, String googleEmail, String displayName) {
        // Caso A: Identidade federada já existe vinculada
        Optional<UserIdentity> existingIdentity = userIdentityRepository.findByProviderAndProviderSubject("GOOGLE", googleSub);
        if (existingIdentity.isPresent()) {
            User user = existingIdentity.get().getUser();
            log.info("Autenticação Google bem-sucedida para identidade já vinculada ao usuário {}", user.getId());
            return user;
        }

        // Caso B: Identidade não encontrada, verificar se existe usuário local com o mesmo e-mail verificado
        Optional<User> existingUser = userRepository.findByEmail(googleEmail);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            log.info("Vinculando identidade Google (sub: {}) ao usuário existente {} com e-mail {}", googleSub, user.getId(), googleEmail);

            UserIdentity newIdentity = new UserIdentity(
                    user,
                    user.getTenantId(),
                    "GOOGLE",
                    googleSub,
                    googleEmail
            );
            userIdentityRepository.save(newIdentity);
            return user;
        }

        // Caso C: Primeiro acesso (Usuário e oficina não cadastrados) -> Auto-provisionamento
        log.info("Provisionando novo Tenant e Usuário para primeiro login Google: {}", googleEmail);
        return autoProvisionUserAndTenant(googleSub, googleEmail, displayName);
    }

    private User autoProvisionUserAndTenant(String googleSub, String googleEmail, String displayName) {
        UUID newTenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(newTenantId);

        String workshopName = (displayName != null && !displayName.isBlank())
                ? "Oficina " + displayName
                : "Oficina GoMech";

        // 1. Criar Tenant
        Tenant tenant = new Tenant();
        tenant.setId(newTenantId);
        tenant.setName(workshopName);
        tenant.setCnpj("00.000.000/" + newTenantId.toString().substring(0, 4) + "-00");
        tenant = tenantRepository.save(tenant);

        // 2. Criar Unidade Matriz
        Unit unit = new Unit();
        unit.setTenantId(newTenantId);
        unit.setName("Matriz");
        unit.setAddress("Endereço a cadastrar");
        unit.setHeadquarters(true);
        unit = unitRepository.save(unit);

        // 3. Criar Role Proprietário
        Role ownerRole = roleRepository.findByName("Proprietário").orElseGet(() -> {
            Role newRole = new Role();
            newRole.setTenantId(newTenantId);
            newRole.setName("Proprietário");
            newRole.setDescription("Acesso total");
            return roleRepository.save(newRole);
        });

        // 4. Criar Usuário
        User user = new User();
        user.setTenantId(tenant.getId());
        user.setName((displayName != null && !displayName.isBlank()) ? displayName : googleEmail);
        user.setEmail(googleEmail);
        // Gerar senha aleatória segura para evitar login sem senha se senha for exigida
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setStatus(UserStatus.ACTIVE);
        user.setLastLogin(OffsetDateTime.now());
        user = userRepository.save(user);

        // 5. Vincular Role ao Usuário na Unidade
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(ownerRole);
        userRole.setUnit(unit);
        userRole.setTenantId(tenant.getId());
        user.getUserRoles().add(userRole);
        user = userRepository.save(user);

        // 6. Criar Identidade Federada
        UserIdentity identity = new UserIdentity(
                user,
                tenant.getId(),
                "GOOGLE",
                googleSub,
                googleEmail
        );
        userIdentityRepository.save(identity);

        return user;
    }

    private AuthResponse issueGoMechTokens(User user, String ipAddress, String userAgent, String deviceInfo) {
        UUID activeUnitId = resolveDefaultUnitId(user);
        List<String> roles = extractRolesForUnit(user, activeUnitId);
        List<String> permissions = extractPermissionsForUnit(user, activeUnitId);

        String accessToken = jwtUtil.generateToken(
                user.getId(),
                user.getTenantId(),
                activeUnitId,
                roles,
                permissions
        );

        String refreshToken = UUID.randomUUID().toString();

        UserSession session = new UserSession();
        session.setUser(user);
        session.setTenantId(user.getTenantId());
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
                user.getTenantId(),
                activeUnitId,
                roles,
                permissions
        );

        return new AuthResponse(accessToken, refreshToken, "Bearer", jwtExpiration / 1000, userSummary);
    }

    private UUID resolveDefaultUnitId(User user) {
        return user.getUserRoles().stream()
                .map(ur -> ur.getUnit() != null ? ur.getUnit().getId() : null)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private List<String> extractRolesForUnit(User user, UUID unitId) {
        return user.getUserRoles().stream()
                .filter(ur -> unitId == null || (ur.getUnit() != null && ur.getUnit().getId().equals(unitId)))
                .map(ur -> ur.getRole().getName())
                .distinct()
                .toList();
    }

    private List<String> extractPermissionsForUnit(User user, UUID unitId) {
        return user.getUserRoles().stream()
                .filter(ur -> unitId == null || (ur.getUnit() != null && ur.getUnit().getId().equals(unitId)))
                .flatMap(ur -> ur.getRole().getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();
    }
}
