package com.gomech.api.modules.iam.api;

import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.iam.application.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterWorkshopRequest request) {
        UUID newTenantId = UUID.randomUUID();
        TenantContextHolder.setTenantId(newTenantId);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(onboardingService.register(request, newTenantId));
        } finally {
            TenantContextHolder.clear();
        }
    }
}
