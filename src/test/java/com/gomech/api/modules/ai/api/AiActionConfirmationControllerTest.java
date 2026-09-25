package com.gomech.api.modules.ai.api;

import com.gomech.api.core.authorization.api.ActorContext;
import com.gomech.api.core.authorization.application.ActorContextProvider;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.core.tenancy.UnitReference;
import com.gomech.api.modules.ai.api.dto.AiActionDtos;
import com.gomech.api.modules.ai.application.AiActionConfirmationService;
import com.gomech.api.modules.ai.domain.AiActionProposalStatus;
import com.gomech.api.modules.ai.domain.AiActionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiActionConfirmationControllerTest {

    @Mock
    private AiActionConfirmationService confirmationService;

    @Mock
    private ActorContextProvider actorContextProvider;

    @InjectMocks
    private AiActionConfirmationController controller;

    private UUID tenantId;
    private UUID userId;
    private UUID unitId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        TenantContextHolder.setTenantId(tenantId);
        lenient().when(actorContextProvider.currentActor())
                .thenReturn(Optional.of(new ActorContext(userId, tenantId, UnitReference.of(unitId), Set.of("AI_ACTION_CONFIRM"), Set.of())));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("POST /api/v1/ai/actions/proposals - Deve criar proposta de ação com sucesso")
    void shouldCreateProposalSuccessfully() {
        AiActionDtos.CreateAiActionProposalRequest request = AiActionDtos.CreateAiActionProposalRequest.builder()
                .actionType(AiActionType.APPLY_QUOTE_ITEMS)
                .title("Aplicar itens de revisão")
                .payloadJson("[]")
                .build();

        AiActionDtos.AiActionProposalResponse mockResponse = AiActionDtos.AiActionProposalResponse.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .unitId(unitId)
                .actionType(AiActionType.APPLY_QUOTE_ITEMS)
                .status(AiActionProposalStatus.PENDING)
                .title("Aplicar itens de revisão")
                .expiresAt(OffsetDateTime.now().plusMinutes(30))
                .build();

        when(confirmationService.createProposal(eq(tenantId), eq(unitId), eq(userId), any(AiActionDtos.CreateAiActionProposalRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<AiActionDtos.AiActionProposalResponse> response = controller.createProposal(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AiActionProposalStatus.PENDING);
        verify(confirmationService).createProposal(eq(tenantId), eq(unitId), eq(userId), any(AiActionDtos.CreateAiActionProposalRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/ai/actions/proposals/{id}/confirm - Deve confirmar e executar ação com sucesso")
    void shouldConfirmProposalSuccessfully() {
        UUID proposalId = UUID.randomUUID();
        AiActionDtos.ConfirmAiActionRequest request = AiActionDtos.ConfirmAiActionRequest.builder()
                .confirmationNotes("Aprovado pelo técnico")
                .build();

        AiActionDtos.AiActionProposalResponse mockResponse = AiActionDtos.AiActionProposalResponse.builder()
                .id(proposalId)
                .tenantId(tenantId)
                .status(AiActionProposalStatus.EXECUTED)
                .confirmedByUserId(userId)
                .confirmedAt(OffsetDateTime.now())
                .build();

        when(confirmationService.confirmAndExecute(eq(tenantId), eq(unitId), eq(userId), eq(proposalId), any(AiActionDtos.ConfirmAiActionRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<AiActionDtos.AiActionProposalResponse> response = controller.confirmProposal(proposalId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AiActionProposalStatus.EXECUTED);
        verify(confirmationService).confirmAndExecute(eq(tenantId), eq(unitId), eq(userId), eq(proposalId), any(AiActionDtos.ConfirmAiActionRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/ai/actions/proposals/{id}/reject - Deve rejeitar proposta com sucesso")
    void shouldRejectProposalSuccessfully() {
        UUID proposalId = UUID.randomUUID();
        AiActionDtos.RejectAiActionRequest request = AiActionDtos.RejectAiActionRequest.builder()
                .rejectionReason("Cliente não aprovou")
                .build();

        AiActionDtos.AiActionProposalResponse mockResponse = AiActionDtos.AiActionProposalResponse.builder()
                .id(proposalId)
                .tenantId(tenantId)
                .status(AiActionProposalStatus.REJECTED)
                .rejectionReason("Cliente não aprovou")
                .build();

        when(confirmationService.rejectProposal(eq(tenantId), eq(unitId), eq(userId), eq(proposalId), any(AiActionDtos.RejectAiActionRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<AiActionDtos.AiActionProposalResponse> response = controller.rejectProposal(proposalId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AiActionProposalStatus.REJECTED);
        verify(confirmationService).rejectProposal(eq(tenantId), eq(unitId), eq(userId), eq(proposalId), any(AiActionDtos.RejectAiActionRequest.class));
    }
}
