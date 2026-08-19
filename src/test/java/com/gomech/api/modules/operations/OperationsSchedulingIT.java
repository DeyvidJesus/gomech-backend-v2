package com.gomech.api.modules.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.crm.api.dto.CreateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.CreateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.CustomerResponse;
import com.gomech.api.modules.crm.api.dto.VehicleResponse;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.operations.api.dto.AppointmentResponse;
import com.gomech.api.modules.operations.api.dto.ChangeAppointmentStatusRequest;
import com.gomech.api.modules.operations.api.dto.CreateAppointmentRequest;
import com.gomech.api.modules.operations.domain.AppointmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class OperationsSchedulingIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record WorkshopContext(String token, UUID unitId, UUID tenantId) {}

    private WorkshopContext registerWorkshopAndGetContext(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@oficina.com.br";
        RegisterWorkshopRequest reg = new RegisterWorkshopRequest(
                "Oficina " + prefix,
                "Av. Principal, 100",
                4,
                List.of("Mecânica Geral"),
                "Dono " + prefix,
                email,
                "SenhaForte@123"
        );

        String regJson = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AuthResponse auth = objectMapper.readValue(regJson, AuthResponse.class);
        return new WorkshopContext("Bearer " + auth.accessToken(), auth.user().activeUnitId(), auth.user().tenantId());
    }

    @Test
    @DisplayName("Deve agendar atendimento com sucesso, consultar detalhes e pesquisar no calendário")
    void shouldScheduleAppointmentAndQueryCalendar() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-sched");

        // 1. Cadastrar cliente e veículo
        CreateCustomerRequest custReq = new CreateCustomerRequest("Marcos Paulo", "529.982.247-25", "(11) 98888-1111", "marcos@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "BRA2E19", "Toyota", "Corolla", 2022, null, 40000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 2. Criar agendamento
        OffsetDateTime scheduledAt = OffsetDateTime.now().plusDays(2);
        OffsetDateTime estimatedEndAt = scheduledAt.plusHours(2);

        CreateAppointmentRequest appReq = new CreateAppointmentRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                scheduledAt,
                estimatedEndAt,
                "Revisão Periódica",
                "Troca de óleo e filtros"
        );

        String appJson = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerName").value("Marcos Paulo"))
                .andExpect(jsonPath("$.licensePlate").value("BRA2E19"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        AppointmentResponse appointment = objectMapper.readValue(appJson, AppointmentResponse.class);

        // 3. Consultar detalhes por ID
        mockMvc.perform(get("/api/v1/appointments/" + appointment.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(appointment.id().toString()))
                .andExpect(jsonPath("$.serviceType").value("Revisão Periódica"));

        // 4. Consultar calendário por intervalo
        OffsetDateTime from = OffsetDateTime.now().plusDays(1);
        OffsetDateTime to = OffsetDateTime.now().plusDays(3);

        mockMvc.perform(get("/api/v1/appointments/calendar")
                        .header("Authorization", ctx.token())
                        .param("from", from.format(DateTimeFormatter.ISO_DATE_TIME))
                        .param("to", to.format(DateTimeFormatter.ISO_DATE_TIME))
                        .param("unitId", ctx.unitId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id").value(appointment.id().toString()))
                .andExpect(jsonPath("$[0].customerName").value("Marcos Paulo"));
    }

    @Test
    @DisplayName("Deve rejeitar agendamento com cliente ou veículo de outro tenant")
    void shouldRejectCrossTenantAppointmentScheduling() throws Exception {
        WorkshopContext ctxA = registerWorkshopAndGetContext("ops-tenant-a");
        WorkshopContext ctxB = registerWorkshopAndGetContext("ops-tenant-b");

        // Criar cliente e veículo no Tenant A
        CreateCustomerRequest custReqA = new CreateCustomerRequest("Cliente A", null, null, null, null);
        String custJsonA = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctxA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReqA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customerA = objectMapper.readValue(custJsonA, CustomerResponse.class);

        CreateVehicleRequest vehReqA = new CreateVehicleRequest(customerA.id(), "ABC1234", "Fiat", "Uno", 2010, null, 100000);
        String vehJsonA = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctxA.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReqA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicleA = objectMapper.readValue(vehJsonA, VehicleResponse.class);

        // Tenant B tenta agendar usando cliente/veículo do Tenant A
        CreateAppointmentRequest appReqB = new CreateAppointmentRequest(
                ctxB.unitId(),
                customerA.id(),
                vehicleA.id(),
                OffsetDateTime.now().plusDays(1),
                null,
                "Revisão",
                null
        );

        mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", ctxB.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReqB)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Customer Vehicle Mismatch"));
    }

    @Test
    @DisplayName("Deve executar ciclo de vida de transições de status e bloquear transições inválidas")
    void shouldExecuteLifecycleTransitions() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-lifecycle");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Lucas Lima", null, null, null, null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "XYZ9999", "VW", "Polo", 2023, null, 15000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        CreateAppointmentRequest appReq = new CreateAppointmentRequest(
                ctx.unitId(), customer.id(), vehicle.id(), OffsetDateTime.now().plusDays(1), null, "Alinhamento", null
        );
        String appJson = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        AppointmentResponse app = objectMapper.readValue(appJson, AppointmentResponse.class);

        // 1. SCHEDULED -> CONFIRMED
        ChangeAppointmentStatusRequest req1 = new ChangeAppointmentStatusRequest(AppointmentStatus.CONFIRMED, null);
        mockMvc.perform(put("/api/v1/appointments/" + app.id() + "/status")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 2. CONFIRMED -> IN_PROGRESS
        ChangeAppointmentStatusRequest req2 = new ChangeAppointmentStatusRequest(AppointmentStatus.IN_PROGRESS, null);
        mockMvc.perform(put("/api/v1/appointments/" + app.id() + "/status")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        // 3. IN_PROGRESS -> COMPLETED
        ChangeAppointmentStatusRequest req3 = new ChangeAppointmentStatusRequest(AppointmentStatus.COMPLETED, null);
        mockMvc.perform(put("/api/v1/appointments/" + app.id() + "/status")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 4. COMPLETED -> SCHEDULED (Inválido) -> 422
        ChangeAppointmentStatusRequest req4 = new ChangeAppointmentStatusRequest(AppointmentStatus.SCHEDULED, null);
        mockMvc.perform(put("/api/v1/appointments/" + app.id() + "/status")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req4)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Invalid Status Transition"));
    }

    @Test
    @DisplayName("Deve cancelar agendamento com motivo")
    void shouldCancelAppointmentWithReason() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-cancel");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Bruno", null, null, null, null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "ABC9A99", "Chevrolet", "Onix", 2021, null, 35000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        CreateAppointmentRequest appReq = new CreateAppointmentRequest(
                ctx.unitId(), customer.id(), vehicle.id(), OffsetDateTime.now().plusDays(1), null, "Troca de Pastilhas", null
        );
        String appJson = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        AppointmentResponse app = objectMapper.readValue(appJson, AppointmentResponse.class);

        // Cancelar agendamento
        mockMvc.perform(delete("/api/v1/appointments/" + app.id())
                        .header("Authorization", ctx.token())
                        .param("reason", "Cliente teve imprevisto"))
                .andExpect(status().isNoContent());

        // Consultar detalhes
        mockMvc.perform(get("/api/v1/appointments/" + app.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELED"))
                .andExpect(jsonPath("$.cancellationReason").value("Cliente teve imprevisto"));
    }
}
