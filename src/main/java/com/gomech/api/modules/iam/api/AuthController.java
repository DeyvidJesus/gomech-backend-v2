package com.gomech.api.modules.iam.api;

import com.gomech.api.modules.iam.api.dto.*;
import com.gomech.api.modules.iam.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "IAM Authentication", description = "Endpoints para autenticação, ciclo de vida de tokens JWT e gestão de sessões")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Autenticação por e-mail e senha", description = "Autentica um usuário existente e emite Access Token JWT e Refresh Token opaco rotacionável.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticação realizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Credenciais inválidas ou usuário inativo")
    })
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

    @Operation(summary = "Renovação de Access Token (Refresh Token Rotation)", description = "Emite um novo par de tokens a partir de um Refresh Token válido. Detecta reuso fraudulento e revoga a família em caso de comprometimento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens rotacionados com sucesso"),
            @ApiResponse(responseCode = "401", description = "Token revogado ou reutilizado ilegalmente (Anti-theft protection)"),
            @ApiResponse(responseCode = "400", description = "Token expirado ou inválido")
    })
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

    @Operation(summary = "Logout da sessão atual", description = "Invalida o Refresh Token fornecido para encerrar a sessão atual.")
    @ApiResponse(responseCode = "204", description = "Sessão encerrada com sucesso")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.refreshToken() != null) {
            authService.logout(request.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Revogar todas as sessões ativas", description = "Invalida todas as sessões e tokens da conta do usuário autenticado.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Todas as sessões foram revogadas")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/revoke-all")
    public ResponseEntity<Void> revokeAllSessions(@AuthenticationPrincipal String userId) {
        if (userId != null) {
            authService.revokeAllSessions(UUID.fromString(userId));
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Alternar unidade ativa", description = "Gera um novo token JWT com escopo e autoridades associados à nova unidade selecionada.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Unidade alternada com sucesso")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/switch-unit")
    public ResponseEntity<AuthResponse> switchUnit(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody SwitchUnitRequest request
    ) {
        return ResponseEntity.ok(authService.switchUnit(UUID.fromString(userId), request.unitId()));
    }

    @Operation(summary = "Listar sessões ativas", description = "Retorna o histórico e status das sessões do usuário autenticado.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "200", description = "Lista de sessões recuperada com sucesso")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> getSessions(
            @AuthenticationPrincipal String userId,
            @RequestParam(value = "currentRefreshToken", required = false) String currentRefreshToken
    ) {
        return ResponseEntity.ok(authService.getActiveSessions(UUID.fromString(userId), currentRefreshToken));
    }

    @Operation(summary = "Revogar sessão específica", description = "Invalida uma sessão individual pelo identificador.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponse(responseCode = "204", description = "Sessão revogada com sucesso")
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
