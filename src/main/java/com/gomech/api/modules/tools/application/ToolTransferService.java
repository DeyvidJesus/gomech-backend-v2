package com.gomech.api.modules.tools.application;

import com.gomech.api.modules.tools.api.dto.ToolTransferDtos;
import com.gomech.api.modules.tools.domain.CustodyEventType;
import com.gomech.api.modules.tools.domain.InvalidToolOperationException;
import com.gomech.api.modules.tools.domain.ToolNotFoundException;
import com.gomech.api.modules.tools.domain.ToolStatus;
import com.gomech.api.modules.tools.domain.ToolTransferStatus;
import com.gomech.api.modules.tools.domain.ToolUnavailableException;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.Tool;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolCustodyLog;
import com.gomech.api.modules.tools.infrastructure.persistence.entity.ToolTransfer;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolCustodyLogRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolRepository;
import com.gomech.api.modules.tools.infrastructure.persistence.repository.ToolTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ToolTransferService {

    private final ToolRepository toolRepository;
    private final ToolTransferRepository transferRepository;
    private final ToolCustodyLogRepository custodyLogRepository;

    @Transactional
    public ToolTransferDtos.Response createTransfer(ToolTransferDtos.Create request, UUID tenantId, UUID requestedByUserId) {
        Tool tool = toolRepository.findByIdAndTenantId(request.toolId(), tenantId)
                .orElseThrow(() -> new ToolNotFoundException(request.toolId()));

        if (tool.getStatus() != ToolStatus.AVAILABLE) {
            throw new ToolUnavailableException(tool.getId(), tool.getStatus());
        }

        if (tool.getUnitId().equals(request.destinationUnitId())) {
            throw new InvalidToolOperationException("A filial de destino não pode ser igual à filial de origem.");
        }

        long count = transferRepository.countByTenantId(tenantId) + 1;
        String transferNumber = String.format("TRFT-%05d", count);

        UUID sourceUnitId = tool.getUnitId();
        tool.setStatus(ToolStatus.IN_TRANSIT);
        tool.setCurrentHolderUserId(null);
        toolRepository.save(tool);

        ToolTransfer transfer = ToolTransfer.builder()
                .tenantId(tenantId)
                .transferNumber(transferNumber)
                .toolId(tool.getId())
                .sourceUnitId(sourceUnitId)
                .destinationUnitId(request.destinationUnitId())
                .status(ToolTransferStatus.IN_TRANSIT)
                .requestedByUserId(requestedByUserId)
                .sentAt(Instant.now())
                .notes(request.notes() != null ? request.notes().trim() : null)
                .build();

        transfer = transferRepository.save(transfer);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(sourceUnitId)
                .toolId(tool.getId())
                .eventType(CustodyEventType.TRANSFER)
                .notes("Remessa para filial " + request.destinationUnitId() + " (" + transferNumber + ")")
                .build();

        custodyLogRepository.save(logEntry);
        log.info("Initiated tool transfer {} toolId={} from unit={} to unit={} for tenant={}",
                transferNumber, tool.getId(), sourceUnitId, request.destinationUnitId(), tenantId);

        return mapToResponse(transfer, tool);
    }

    @Transactional
    public ToolTransferDtos.Response completeTransfer(UUID transferId, UUID tenantId, UUID receivedByUserId) {
        ToolTransfer transfer = transferRepository.findByIdAndTenantId(transferId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found with ID: " + transferId));

        if (transfer.getStatus() != ToolTransferStatus.IN_TRANSIT && transfer.getStatus() != ToolTransferStatus.PENDING) {
            throw new InvalidToolOperationException("Esta transferência já foi finalizada ou cancelada.");
        }

        transfer.setStatus(ToolTransferStatus.COMPLETED);
        transfer.setReceivedByUserId(receivedByUserId);
        transfer.setReceivedAt(Instant.now());
        transfer = transferRepository.save(transfer);

        UUID toolId = transfer.getToolId();
        Tool tool = toolRepository.findByIdAndTenantId(toolId, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(toolId));

        tool.setUnitId(transfer.getDestinationUnitId());
        tool.setStatus(ToolStatus.AVAILABLE);
        tool.setCurrentHolderUserId(null);
        toolRepository.save(tool);

        ToolCustodyLog logEntry = ToolCustodyLog.builder()
                .tenantId(tenantId)
                .unitId(transfer.getDestinationUnitId())
                .toolId(tool.getId())
                .toUserId(receivedByUserId)
                .eventType(CustodyEventType.TRANSFER)
                .notes("Recebimento concluído na filial destino (" + transfer.getTransferNumber() + ")")
                .build();

        custodyLogRepository.save(logEntry);
        log.info("Completed tool transfer {} toolId={} arrived at unit={}",
                transfer.getTransferNumber(), tool.getId(), transfer.getDestinationUnitId());

        return mapToResponse(transfer, tool);
    }

    @Transactional
    public ToolTransferDtos.Response cancelTransfer(UUID transferId, String reason, UUID tenantId) {
        ToolTransfer transfer = transferRepository.findByIdAndTenantId(transferId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found with ID: " + transferId));

        if (transfer.getStatus() != ToolTransferStatus.IN_TRANSIT && transfer.getStatus() != ToolTransferStatus.PENDING) {
            throw new InvalidToolOperationException("Esta transferência não pode mais ser cancelada.");
        }

        transfer.setStatus(ToolTransferStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            transfer.setNotes(transfer.getNotes() != null ? transfer.getNotes() + " | Cancelamento: " + reason.trim() : reason.trim());
        }
        transfer = transferRepository.save(transfer);

        UUID toolId = transfer.getToolId();
        Tool tool = toolRepository.findByIdAndTenantId(toolId, tenantId)
                .orElseThrow(() -> new ToolNotFoundException(toolId));

        tool.setStatus(ToolStatus.AVAILABLE);
        toolRepository.save(tool);

        return mapToResponse(transfer, tool);
    }

    @Transactional(readOnly = true)
    public Page<ToolTransferDtos.Response> listTransfers(UUID tenantId, UUID unitId, ToolTransferStatus status, Pageable pageable) {
        return transferRepository.findAllByTenantId(tenantId, unitId, status, pageable)
                .map(t -> {
                    Tool tool = toolRepository.findByIdAndTenantId(t.getToolId(), tenantId).orElse(null);
                    return mapToResponse(t, tool);
                });
    }

    private ToolTransferDtos.Response mapToResponse(ToolTransfer transfer, Tool tool) {
        return ToolTransferDtos.Response.builder()
                .id(transfer.getId())
                .tenantId(transfer.getTenantId())
                .transferNumber(transfer.getTransferNumber())
                .toolId(transfer.getToolId())
                .toolName(tool != null ? tool.getName() : "Ferramenta")
                .toolAssetTag(tool != null ? tool.getAssetTag() : "-")
                .sourceUnitId(transfer.getSourceUnitId())
                .destinationUnitId(transfer.getDestinationUnitId())
                .status(transfer.getStatus())
                .requestedByUserId(transfer.getRequestedByUserId())
                .receivedByUserId(transfer.getReceivedByUserId())
                .sentAt(transfer.getSentAt())
                .receivedAt(transfer.getReceivedAt())
                .notes(transfer.getNotes())
                .version(transfer.getVersion())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }
}
