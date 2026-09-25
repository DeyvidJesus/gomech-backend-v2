package com.gomech.api.modules.ai.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gomech.api.core.audit.application.AuditRecorder;
import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.ai.api.dto.AiActionDtos;
import com.gomech.api.modules.ai.domain.*;
import com.gomech.api.modules.ai.events.AiActionExecutedEvent;
import com.gomech.api.modules.ai.events.AiActionProposedEvent;
import com.gomech.api.modules.ai.events.AiActionRejectedEvent;
import com.gomech.api.modules.ai.infrastructure.persistence.entity.AiActionProposal;
import com.gomech.api.modules.ai.infrastructure.persistence.repository.AiActionProposalRepository;
import com.gomech.api.modules.operations.api.OperationsActionContract;
import com.gomech.api.modules.operations.api.dto.QuoteResponse;
import com.gomech.api.modules.operations.domain.CustomerApprovalStatus;
import com.gomech.api.modules.operations.domain.QuoteStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiActionConfirmationServiceTest {

    @Mock
    private AiActionProposalRepository proposalRepository;

    @Mock
    private OperationsActionContract operationsActionContract;

    @Mock
    private AuditRecorder auditRecorder;

    @Mock
    private DomainEventBus eventBus;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private AiActionConfirmationService confirmationService;

    private UUID tenantId;
    private UUID unitId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        unitId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Deve criar proposta de ação com status PENDING e publicar evento AiActionProposedEvent")
    void shouldCreateProposalSuccessfully() {
        AiActionDtos.CreateAiActionProposalRequest request = AiActionDtos.CreateAiActionProposalRequest.builder()
                .actionType(AiActionType.APPLY_QUOTE_ITEMS)
                .targetResourceType("QUOTE")
                .targetResourceId(UUID.randomUUID())
                .title("Aplicar 2 itens de ignição recomendados")
                .summary("Substituição de velas e limpeza de bicos")
                .payloadJson("[{\"name\":\"Jogo de Velas\",\"quantity\":1,\"unitPrice\":240.00}]")
                .ttlMinutes(30)
                .build();

        when(proposalRepository.save(any(AiActionProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiActionDtos.AiActionProposalResponse response = confirmationService.createProposal(tenantId, unitId, userId, request);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(AiActionProposalStatus.PENDING);
        assertThat(response.actionType()).isEqualTo(AiActionType.APPLY_QUOTE_ITEMS);
        assertThat(response.title()).isEqualTo("Aplicar 2 itens de ignição recomendados");
        assertThat(response.expiresAt()).isAfter(OffsetDateTime.now());

        verify(proposalRepository).save(any(AiActionProposal.class));
        verify(eventBus).publish(any(AiActionProposedEvent.class));
    }

    @Test
    @DisplayName("Deve confirmar e executar ação de orçamento com sucesso e registrar auditoria")
    void shouldConfirmAndExecuteQuoteActionSuccessfully() {
        UUID proposalId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();

        AiActionProposal pendingProposal = AiActionProposal.builder()
                .id(proposalId)
                .tenantId(tenantId)
                .unitId(unitId)
                .actorUserId(userId)
                .actionType(AiActionType.APPLY_QUOTE_ITEMS)
                .targetResourceType("QUOTE")
                .targetResourceId(quoteId)
                .title("Adicionar velas")
                .payloadJson("[{\"name\":\"Vela Iridium\",\"quantity\":4,\"unitPrice\":60.00}]")
                .status(AiActionProposalStatus.PENDING)
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .version(0L)
                .createdAt(OffsetDateTime.now().minusMinutes(5))
                .updatedAt(OffsetDateTime.now().minusMinutes(5))
                .build();

        QuoteResponse mockQuoteResponse = new QuoteResponse(
                quoteId, unitId, UUID.randomUUID(), "Carlos", "12345678900",
                UUID.randomUUID(), "ABC1234", "ABC-1234", "VW", "Gol", 2020,
                null, null, userId, null, null,
                QuoteStatus.DRAFT, CustomerApprovalStatus.PENDING, null, null,
                BigDecimal.valueOf(240.00), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.valueOf(240.00), BigDecimal.valueOf(240.00),
                OffsetDateTime.now().plusDays(10), null, null, List.of(),
                OffsetDateTime.now(), OffsetDateTime.now(), 0L
        );

        when(proposalRepository.findByIdAndTenantId(proposalId, tenantId)).thenReturn(Optional.of(pendingProposal));
        when(operationsActionContract.applyProposedQuoteItems(eq(quoteId), eq(tenantId), eq(unitId), eq(userId), anyList()))
                .thenReturn(mockQuoteResponse);
        when(proposalRepository.save(any(AiActionProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiActionDtos.ConfirmAiActionRequest confirmRequest = AiActionDtos.ConfirmAiActionRequest.builder()
                .confirmationNotes("Confirmado pelo mecânico chefe")
                .build();

        AiActionDtos.AiActionProposalResponse response = confirmationService.confirmAndExecute(
                tenantId, unitId, userId, proposalId, confirmRequest);

        assertThat(response.status()).isEqualTo(AiActionProposalStatus.EXECUTED);
        assertThat(response.confirmedByUserId()).isEqualTo(userId);
        assertThat(response.confirmedAt()).isNotNull();
        assertThat(response.executedAt()).isNotNull();

        verify(operationsActionContract).applyProposedQuoteItems(eq(quoteId), eq(tenantId), eq(unitId), eq(userId), anyList());
        verify(auditRecorder).record(any(), any());
        verify(eventBus).publish(any(AiActionExecutedEvent.class));
    }

    @Test
    @DisplayName("Replay Protection: Deve lançar AiActionAlreadyProcessedException ao tentar confirmar proposta já executada")
    void shouldPreventReplayOnAlreadyExecutedProposal() {
        UUID proposalId = UUID.randomUUID();

        AiActionProposal alreadyExecutedProposal = AiActionProposal.builder()
                .id(proposalId)
                .tenantId(tenantId)
                .unitId(unitId)
                .actorUserId(userId)
                .actionType(AiActionType.APPLY_QUOTE_ITEMS)
                .status(AiActionProposalStatus.EXECUTED)
                .expiresAt(OffsetDateTime.now().plusMinutes(15))
                .build();

        when(proposalRepository.findByIdAndTenantId(proposalId, tenantId)).thenReturn(Optional.of(alreadyExecutedProposal));

        assertThrows(AiActionAlreadyProcessedException.class, () ->
                confirmationService.confirmAndExecute(tenantId, unitId, userId, proposalId, null));

        verifyNoInteractions(operationsActionContract);
    }

    @Test
    @DisplayName("TTL Expiration: Deve lançar AiActionExpiredException e marcar status EXPIRED quando o tempo limite for ultrapassado")
    void shouldRejectExpiredProposal() {
        UUID proposalId = UUID.randomUUID();

        AiActionProposal expiredProposal = AiActionProposal.builder()
                .id(proposalId)
                .tenantId(tenantId)
                .unitId(unitId)
                .actorUserId(userId)
                .actionType(AiActionType.APPLY_QUOTE_ITEMS)
                .status(AiActionProposalStatus.PENDING)
                .expiresAt(OffsetDateTime.now().minusMinutes(5)) // Expirado há 5 minutos
                .build();

        when(proposalRepository.findByIdAndTenantId(proposalId, tenantId)).thenReturn(Optional.of(expiredProposal));
        when(proposalRepository.save(any(AiActionProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThrows(AiActionExpiredException.class, () ->
                confirmationService.confirmAndExecute(tenantId, unitId, userId, proposalId, null));

        ArgumentCaptor<AiActionProposal> proposalCaptor = ArgumentCaptor.forClass(AiActionProposal.class);
        verify(proposalRepository).save(proposalCaptor.capture());
        assertThat(proposalCaptor.getValue().getStatus()).isEqualTo(AiActionProposalStatus.EXPIRED);
        verifyNoInteractions(operationsActionContract);
    }

    @Test
    @DisplayName("Deve rejeitar proposta explicitamente com justificativa e publicar AiActionRejectedEvent")
    void shouldRejectProposalSuccessfully() {
        UUID proposalId = UUID.randomUUID();

        AiActionProposal pendingProposal = AiActionProposal.builder()
                .id(proposalId)
                .tenantId(tenantId)
                .unitId(unitId)
                .actorUserId(userId)
                .actionType(AiActionType.APPLY_QUOTE_ITEMS)
                .status(AiActionProposalStatus.PENDING)
                .expiresAt(OffsetDateTime.now().plusMinutes(20))
                .build();

        when(proposalRepository.findByIdAndTenantId(proposalId, tenantId)).thenReturn(Optional.of(pendingProposal));
        when(proposalRepository.save(any(AiActionProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AiActionDtos.RejectAiActionRequest rejectRequest = AiActionDtos.RejectAiActionRequest.builder()
                .rejectionReason("Cliente optou por realizar o serviço em outra data")
                .build();

        AiActionDtos.AiActionProposalResponse response = confirmationService.rejectProposal(
                tenantId, unitId, userId, proposalId, rejectRequest);

        assertThat(response.status()).isEqualTo(AiActionProposalStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Cliente optou por realizar o serviço em outra data");
        assertThat(response.confirmedByUserId()).isEqualTo(userId);

        verify(auditRecorder).record(any(), any());
        verify(eventBus).publish(any(AiActionRejectedEvent.class));
        verifyNoInteractions(operationsActionContract);
    }
}
