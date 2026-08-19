package com.gomech.api.modules.iam.application;

import com.gomech.api.core.security.JwtUtil;
import com.gomech.api.modules.iam.api.dto.*;
import com.gomech.api.modules.iam.domain.UserStatus;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Permission;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Role;
import com.gomech.api.modules.iam.infrastructure.persistence.model.Unit;
import com.gomech.api.modules.iam.infrastructure.persistence.model.User;
import com.gomech.api.modules.iam.infrastructure.persistence.model.UserRole;
import com.gomech.api.modules.iam.infrastructure.persistence.model.UserSession;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UnitRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserRepository;
import com.gomech.api.modules.iam.infrastructure.persistence.repository.UserSessionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final UnitRepository unitRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    @Value("${jwt.refresh-expiration}")
    private long jwtRefreshExpiration;

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent, String deviceInfo) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Credenciais inválidas"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }

        if (!UserStatus.isActive(user.getStatus())) {
            throw new IllegalArgumentException("Usuário inativo ou suspenso");
        }

        user.setLastLogin(OffsetDateTime.now());
        userRepository.save(user);

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

    @Transactional(noRollbackFor = SecurityException.class)
    public AuthResponse refreshToken(RefreshTokenRequest request, String ipAddress, String userAgent, String deviceInfo) {
        UserSession session = userSessionRepository.findByRefreshToken(request.refreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inválido ou não encontrado"));

        // Reuse Detection (Theft protection)
        if (session.isRevoked()) {
            log.warn("SECURITY ALERT: Compromised or reused refresh token presented! Family ID: {}, User ID: {}",
                    session.getFamilyId(), session.getUser().getId());
            userSessionRepository.revokeAllByFamilyId(session.getFamilyId(), OffsetDateTime.now());
            throw new SecurityException("Token de atualização revogado ou reutilizado ilegalmente. Todas as sessões da família foram invalidadas por segurança.");
        }

        if (session.isExpired()) {
            session.revoke();
            userSessionRepository.save(session);
            throw new IllegalArgumentException("Sessão expirada. Por favor, realize novo login.");
        }

        User user = session.getUser();
        if (!UserStatus.isActive(user.getStatus())) {
            session.revoke();
            userSessionRepository.save(session);
            throw new IllegalArgumentException("Usuário inativo ou suspenso");
        }

        // Rotate Refresh Token
        session.setRevoked(true);
        session.setRevokedAt(OffsetDateTime.now());
        session.setLastUsedAt(OffsetDateTime.now());

        String newRefreshToken = UUID.randomUUID().toString();

        UserSession newSession = new UserSession();
        newSession.setUser(user);
        newSession.setTenantId(user.getTenantId());
        newSession.setFamilyId(session.getFamilyId());
        newSession.setRefreshToken(newRefreshToken);
        newSession.setExpiresAt(OffsetDateTime.now().plusSeconds(jwtRefreshExpiration / 1000));
        newSession.setIpAddress(ipAddress != null ? ipAddress : session.getIpAddress());
        newSession.setUserAgent(userAgent != null ? userAgent : session.getUserAgent());
        newSession.setDeviceInfo(deviceInfo != null ? deviceInfo : session.getDeviceInfo());
        newSession.setLastUsedAt(OffsetDateTime.now());
        newSession.setRevoked(false);
        newSession = userSessionRepository.save(newSession);

        session.setReplacedBy(newSession.getId());
        userSessionRepository.save(session);

        UUID activeUnitId = resolveDefaultUnitId(user);
        List<String> roles = extractRolesForUnit(user, activeUnitId);
        List<String> permissions = extractPermissionsForUnit(user, activeUnitId);

        String newAccessToken = jwtUtil.generateToken(
                user.getId(),
                user.getTenantId(),
                activeUnitId,
                roles,
                permissions
        );

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getTenantId(),
                activeUnitId,
                roles,
                permissions
        );

        return new AuthResponse(newAccessToken, newRefreshToken, "Bearer", jwtExpiration / 1000, userSummary);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        userSessionRepository.findByRefreshToken(refreshToken).ifPresent(session -> {
            if (!session.isRevoked()) {
                session.revoke();
                userSessionRepository.save(session);
            }
        });
    }

    @Transactional
    public void revokeAllSessions(UUID userId) {
        userSessionRepository.revokeAllByUserId(userId, OffsetDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getActiveSessions(UUID userId, String currentRefreshToken) {
        return userSessionRepository.findAllByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId).stream()
                .filter(session -> !session.isExpired())
                .map(session -> new SessionResponse(
                        session.getId(),
                        session.getFamilyId(),
                        session.getCreatedAt(),
                        session.getLastUsedAt(),
                        session.getExpiresAt(),
                        session.getIpAddress(),
                        session.getUserAgent(),
                        session.getDeviceInfo(),
                        session.getRefreshToken().equals(currentRefreshToken)
                ))
                .toList();
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId) {
        userSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getUser().getId().equals(userId) && !session.isRevoked()) {
                session.revoke();
                userSessionRepository.save(session);
            }
        });
    }

    @Transactional(readOnly = true)
    public AuthResponse switchUnit(UUID userId, UUID targetUnitId) {
        User user = userRepository.findByIdWithRoles(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (!UserStatus.isActive(user.getStatus())) {
            throw new IllegalArgumentException("Usuário inativo ou suspenso");
        }

        Unit targetUnit = unitRepository.findById(targetUnitId)
                .orElseThrow(() -> new IllegalArgumentException("Unidade não encontrada: " + targetUnitId));

        if (!targetUnit.getTenantId().equals(user.getTenantId())) {
            throw new IllegalArgumentException("Acesso negado: a unidade solicitada pertence a outra organização");
        }

        boolean hasAccessToUnit = user.getUserRoles().stream()
                .anyMatch(ur -> (ur.getRole() != null && "Proprietário".equals(ur.getRole().getName()))
                        || ur.getUnit() == null
                        || (ur.getUnit() != null && ur.getUnit().getId().equals(targetUnitId)));

        if (!hasAccessToUnit) {
            throw new IllegalArgumentException("Usuário não possui acesso à unidade especificada");
        }

        List<String> roles = extractRolesForUnit(user, targetUnitId);
        List<String> permissions = extractPermissionsForUnit(user, targetUnitId);

        String newAccessToken = jwtUtil.generateToken(
                user.getId(),
                user.getTenantId(),
                targetUnitId,
                roles,
                permissions
        );

        UserSummaryDto userSummary = new UserSummaryDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getTenantId(),
                targetUnitId,
                roles,
                permissions
        );

        return new AuthResponse(newAccessToken, null, "Bearer", jwtExpiration / 1000, userSummary);
    }

    private UUID resolveDefaultUnitId(User user) {
        if (user.getUserRoles() == null || user.getUserRoles().isEmpty()) {
            return null;
        }
        return user.getUserRoles().stream()
                .filter(ur -> ur.getUnit() != null)
                .map(ur -> ur.getUnit().getId())
                .findFirst()
                .orElse(null);
    }

    private List<String> extractRolesForUnit(User user, UUID unitId) {
        if (user.getUserRoles() == null) {
            return List.of();
        }
        return user.getUserRoles().stream()
                .filter(ur -> (ur.getRole() != null && "Proprietário".equals(ur.getRole().getName()))
                        || ur.getUnit() == null
                        || (unitId != null && ur.getUnit() != null && ur.getUnit().getId().equals(unitId)))
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .map(Role::getName)
                .distinct()
                .toList();
    }

    private List<String> extractPermissionsForUnit(User user, UUID unitId) {
        if (user.getUserRoles() == null) {
            return List.of();
        }
        return user.getUserRoles().stream()
                .filter(ur -> (ur.getRole() != null && "Proprietário".equals(ur.getRole().getName()))
                        || ur.getUnit() == null
                        || (unitId != null && ur.getUnit() != null && ur.getUnit().getId().equals(unitId)))
                .map(UserRole::getRole)
                .filter(Objects::nonNull)
                .flatMap(r -> r.getPermissions().stream())
                .map(Permission::getCode)
                .distinct()
                .toList();
    }
}
