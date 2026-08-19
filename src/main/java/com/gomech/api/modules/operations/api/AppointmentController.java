package com.gomech.api.modules.operations.api;

import com.gomech.api.core.api.PageResponse;
import com.gomech.api.modules.operations.api.dto.AppointmentResponse;
import com.gomech.api.modules.operations.api.dto.AppointmentSummaryResponse;
import com.gomech.api.modules.operations.api.dto.ChangeAppointmentStatusRequest;
import com.gomech.api.modules.operations.api.dto.CreateAppointmentRequest;
import com.gomech.api.modules.operations.api.dto.UpdateAppointmentRequest;
import com.gomech.api.modules.operations.application.AppointmentService;
import com.gomech.api.modules.operations.domain.AppointmentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Operations - Appointments", description = "Gestão de agendamentos e calendário de serviços da oficina")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @PreAuthorize("hasAuthority('OPERATIONS_APPOINTMENT_WRITE') or hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Criar novo agendamento de serviço", description = "Agenda um atendimento vinculando cliente, veículo e data/hora com validação de escopo")
    public ResponseEntity<AppointmentResponse> scheduleAppointment(
            @Valid @RequestBody CreateAppointmentRequest request
    ) {
        AppointmentResponse response = appointmentService.scheduleAppointment(request, null, null);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/calendar")
    @PreAuthorize("hasAuthority('OPERATIONS_APPOINTMENT_READ') or hasAuthority('OPERATIONS_ORDER_READ') or hasRole('Proprietário')")
    @Operation(summary = "Consultar agendamentos por intervalo de calendário", description = "Retorna todos os agendamentos ativos no intervalo de datas especificado")
    public ResponseEntity<List<AppointmentSummaryResponse>> getCalendarAppointments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) UUID unitId
    ) {
        List<AppointmentSummaryResponse> appointments = appointmentService.getCalendarAppointments(from, to, unitId, null);
        return ResponseEntity.ok(appointments);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OPERATIONS_APPOINTMENT_READ') or hasAuthority('OPERATIONS_ORDER_READ') or hasRole('Proprietário')")
    @Operation(summary = "Listar agendamentos com paginação e filtros", description = "Retorna lista paginada de agendamentos no envelope padrão PageResponse")
    public ResponseEntity<PageResponse<AppointmentSummaryResponse>> searchAppointments(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "scheduledAt,asc") String sort
    ) {
        if (size > 100) {
            throw new IllegalArgumentException("O tamanho máximo da página permitido é 100.");
        }

        Sort sortOrder = Sort.by(Sort.Direction.ASC, "scheduledAt");
        if (sort != null && !sort.isBlank()) {
            String[] sortParts = sort.split(",");
            String property = sortParts[0].trim();
            Sort.Direction direction = (sortParts.length > 1 && "desc".equalsIgnoreCase(sortParts[1].trim()))
                    ? Sort.Direction.DESC
                    : Sort.Direction.ASC;
            sortOrder = Sort.by(direction, property);
        }

        Pageable pageable = PageRequest.of(page, size, sortOrder);
        Page<AppointmentSummaryResponse> results = appointmentService.searchAppointments(status, unitId, pageable, null);

        return ResponseEntity.ok(PageResponse.from(results));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('OPERATIONS_APPOINTMENT_READ') or hasAuthority('OPERATIONS_ORDER_READ') or hasRole('Proprietário')")
    @Operation(summary = "Consultar detalhes do agendamento por ID", description = "Retorna dados completos do agendamento, cliente e veículo")
    public ResponseEntity<AppointmentResponse> getAppointmentById(@PathVariable UUID id) {
        AppointmentResponse response = appointmentService.getAppointmentById(id, null, null);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('OPERATIONS_APPOINTMENT_WRITE') or hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Atualizar agendamento", description = "Atualiza data/hora, tipo de serviço e observações de um agendamento não finalizado")
    public ResponseEntity<AppointmentResponse> updateAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentRequest request
    ) {
        AppointmentResponse response = appointmentService.updateAppointment(id, request, null, null);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('OPERATIONS_APPOINTMENT_WRITE') or hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Alterar status do agendamento", description = "Executa transição de status no ciclo de vida do agendamento")
    public ResponseEntity<AppointmentResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeAppointmentStatusRequest request
    ) {
        AppointmentResponse response = appointmentService.changeStatus(id, request, null, null);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OPERATIONS_APPOINTMENT_CANCEL') or hasAuthority('OPERATIONS_ORDER_WRITE') or hasRole('Proprietário')")
    @Operation(summary = "Cancelar agendamento", description = "Cancela o agendamento informando o motivo opcional")
    public ResponseEntity<Void> cancelAppointment(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason
    ) {
        appointmentService.cancelAppointment(id, reason, null, null);
        return ResponseEntity.noContent().build();
    }
}
