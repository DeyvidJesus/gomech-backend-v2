package com.gomech.api.modules.iam.api;

import com.gomech.api.modules.iam.api.dto.*;
import com.gomech.api.modules.iam.application.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceInfo = httpRequest.getHeader("X-Device-Info");
        return ResponseEntity.ok(authService.login(request, ipAddress, userAgent, deviceInfo));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        String deviceInfo = httpRequest.getHeader("X-Device-Info");
        return ResponseEntity.ok(authService.refreshToken(request, ipAddress, userAgent, deviceInfo));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.refreshToken() != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/revoke-all")
    public ResponseEntity<Void> revokeAllSessions(@AuthenticationPrincipal String userId) {
        if (userId != null) {
            authService.revokeAllSessions(UUID.fromString(userId));
        }
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/switch-unit")
    public ResponseEntity<AuthResponse> switchUnit(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SwitchUnitRequest request
    ) {
        return ResponseEntity.ok(authService.switchUnit(UUID.fromString(userId), request.unitId()));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getSessions(
            @AuthenticationPrincipal String userId,
            @RequestParam(value = "currentRefreshToken", required = false) String currentRefreshToken
    ) {
        return ResponseEntity.ok(authService.getActiveSessions(UUID.fromString(userId), currentRefreshToken));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @AuthenticationPrincipal String userId,
            @PathVariable UUID sessionId
    ) {
        authService.revokeSession(UUID.fromString(userId), sessionId);
        return ResponseEntity.noContent().build();
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
