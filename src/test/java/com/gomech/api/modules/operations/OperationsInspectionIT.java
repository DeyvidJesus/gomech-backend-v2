package com.gomech.api.modules.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.crm.api.dto.CreateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.CreateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.CustomerResponse;
import com.gomech.api.modules.crm.api.dto.VehicleResponse;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
import com.gomech.api.modules.operations.api.dto.AppointmentResponse;
import com.gomech.api.modules.operations.api.dto.CompleteInspectionRequest;
import com.gomech.api.modules.operations.api.dto.CreateAppointmentRequest;
import com.gomech.api.modules.operations.api.dto.CreateInspectionRequest;
import com.gomech.api.modules.operations.api.dto.InspectionResponse;
import com.gomech.api.modules.operations.api.dto.SaveInspectionItemRequest;
import com.gomech.api.modules.operations.api.dto.UpdateInspectionRequest;
import com.gomech.api.modules.operations.domain.FuelLevel;
import com.gomech.api.modules.operations.domain.InspectionCategory;
import com.gomech.api.modules.operations.domain.InspectionItemStatus;
import com.gomech.api.modules.operations.domain.InspectionStatus;
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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class OperationsInspectionIT {

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
    @DisplayName("Deve criar inspeção com checklist, atualizar, finalizar e impedir alteração pós-finalização")
    void shouldCreateInspectionAndUpdateItemsAndCompleteSuccessfully() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-insp");

        // 1. Cadastrar cliente e veículo
        CreateCustomerRequest custReq = new CreateCustomerRequest("Rodrigo Santos", "529.982.247-25", "(11) 97777-2222", "rodrigo@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "BRA2E19", "Toyota", "Corolla", 2022, "12345678901234567", 45000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 2. Criar inspeção com itens de checklist
        SaveInspectionItemRequest item1 = new SaveInspectionItemRequest(
                null,
                InspectionCategory.BRAKES,
                "Pastilhas de freio dianteiras",
                InspectionItemStatus.CRITICAL,
                "Espessura menor que 2mm",
                "Substituir pastilhas e retificar discos",
                "http://img.gomech.com/pastilha.jpg"
        );
        SaveInspectionItemRequest item2 = new SaveInspectionItemRequest(
                null,
                InspectionCategory.TIRES,
                "Pneu dianteiro esquerdo",
                InspectionItemStatus.ATTENTION,
                "Desgaste irregular na banda interna",
                "Realizar alinhamento e balanceamento",
                null
        );
        SaveInspectionItemRequest item3 = new SaveInspectionItemRequest(
                null,
                InspectionCategory.FLUIDS,
                "Óleo do motor",
                InspectionItemStatus.OK,
                "Nível e viscosidade adequados",
                null,
                null
        );

        CreateInspectionRequest inspReq = new CreateInspectionRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                null,
                FuelLevel.HALF,
                45000,
                "Vistoria de entrada para revisão",
                List.of(item1, item2, item3)
        );

        String inspJson = mockMvc.perform(post("/api/v1/inspections")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inspReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.fuelLevel").value("HALF"))
                .andExpect(jsonPath("$.currentMileage").value(45000))
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.criticalItems").value(1))
                .andExpect(jsonPath("$.attentionItems").value(1))
                .andExpect(jsonPath("$.okItems").value(1))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        InspectionResponse inspection = objectMapper.readValue(inspJson, InspectionResponse.class);
        UUID inspectionId = inspection.id();

        // 3. Consultar detalhes da inspeção
        mockMvc.perform(get("/api/v1/inspections/" + inspectionId)
                        .header("Authorization", ctx.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Rodrigo Santos"))
                .andExpect(jsonPath("$.formattedLicensePlate").value("BRA2E19"))
                .andExpect(jsonPath("$.items", hasSize(3)));

        // 4. Atualizar dados gerais da inspeção
        UpdateInspectionRequest updateReq = new UpdateInspectionRequest(FuelLevel.THREE_QUARTERS, 45050, "Atualizando notas gerais");
        mockMvc.perform(put("/api/v1/inspections/" + inspectionId)
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fuelLevel").value("THREE_QUARTERS"))
                .andExpect(jsonPath("$.currentMileage").value(45050))
                .andExpect(jsonPath("$.generalNotes").value("Atualizando notas gerais"));

        // 5. Finalizar a inspeção
        CompleteInspectionRequest completeReq = new CompleteInspectionRequest("Vistoria completa e laudo finalizado para orçamento.", null);
        mockMvc.perform(post("/api/v1/inspections/" + inspectionId + "/complete")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(completeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.generalNotes").value("Vistoria completa e laudo finalizado para orçamento."));

        // 6. Tentar modificar inspeção já finalizada deve retornar 422
        mockMvc.perform(put("/api/v1/inspections/" + inspectionId)
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Inspection Already Finalized"));
    }

    @Test
    @DisplayName("Deve criar inspeção vinculada a agendamento existente")
    void shouldCreateInspectionLinkedToAppointment() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-insp-app");

        // 1. Cadastrar cliente, veículo e agendamento
        CreateCustomerRequest custReq = new CreateCustomerRequest("Ana Beatriz", "529.982.247-25", "(11) 96666-3333", "ana@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "XYZ9K88", "Honda", "Civic", 2021, "98765432109876543", 30000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        CreateAppointmentRequest appReq = new CreateAppointmentRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(1).plusHours(2),
                "Revisão Periódica",
                "Cliente relatou ruído na suspensão"
        );
        String appJson = mockMvc.perform(post("/api/v1/appointments")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(appReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        AppointmentResponse appointment = objectMapper.readValue(appJson, AppointmentResponse.class);

        // 2. Criar inspeção informando appointmentId
        CreateInspectionRequest inspReq = new CreateInspectionRequest(
                ctx.unitId(),
                customer.id(),
                vehicle.id(),
                appointment.id(),
                FuelLevel.FULL,
                30000,
                "Inspeção de agendamento",
                List.of()
        );

        mockMvc.perform(post("/api/v1/inspections")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inspReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.appointmentId").value(appointment.id().toString()))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("Deve rejeitar inspeção quando cliente e veículo não forem associados")
    void shouldRejectInspectionWithMismatchedCustomerAndVehicle() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-insp-mismatch");

        // Cliente 1 e Veículo 1
        CreateCustomerRequest cust1 = new CreateCustomerRequest("Cliente 1", null, "(11) 91111-0000", "c1@email.com", null);
        String c1Json = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cust1))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse custResp1 = objectMapper.readValue(c1Json, CustomerResponse.class);

        // Cliente 2 e Veículo 2
        CreateCustomerRequest cust2 = new CreateCustomerRequest("Cliente 2", null, "(11) 92222-0000", "c2@email.com", null);
        String c2Json = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(cust2))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse custResp2 = objectMapper.readValue(c2Json, CustomerResponse.class);

        CreateVehicleRequest veh2 = new CreateVehicleRequest(custResp2.id(), "KTR1A23", "Ford", "Ka", 2020, "11112222333344445", 20000);
        String v2Json = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(veh2))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehResp2 = objectMapper.readValue(v2Json, VehicleResponse.class);

        // Tenta criar inspeção com Cliente 1 e Veículo 2 (do Cliente 2)
        CreateInspectionRequest invalidReq = new CreateInspectionRequest(
                ctx.unitId(),
                custResp1.id(),
                vehResp2.id(),
                null,
                FuelLevel.FULL,
                20000,
                null,
                List.of()
        );

        mockMvc.perform(post("/api/v1/inspections")
                        .header("Authorization", ctx.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Customer Vehicle Mismatch"));
    }

    @Test
    @DisplayName("Deve buscar inspeções paginadas e cancelar inspeção com sucesso")
    void shouldSearchAndCancelInspection() throws Exception {
        WorkshopContext ctx = registerWorkshopAndGetContext("ops-insp-search");

        CreateCustomerRequest custReq = new CreateCustomerRequest("Fernando Costa", "529.982.247-25", "(11) 93333-4444", "fernando@email.com", null);
        String custJson = mockMvc.perform(post("/api/v1/customers").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(custReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "FGH5J67", "VW", "Golf", 2019, "55556666777788889", 60000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(vehReq))).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        CreateInspectionRequest inspReq = new CreateInspectionRequest(ctx.unitId(), customer.id(), vehicle.id(), null, FuelLevel.RESERVE, 60000, "Inspeção para cancelamento", List.of());
        String inspJson = mockMvc.perform(post("/api/v1/inspections").header("Authorization", ctx.token()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(inspReq))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        InspectionResponse inspection = objectMapper.readValue(inspJson, InspectionResponse.class);

        // Listagem paginada
        mockMvc.perform(get("/api/v1/inspections")
                        .header("Authorization", ctx.token())
                        .param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(1)));

        // Cancelar inspeção
        mockMvc.perform(delete("/api/v1/inspections/" + inspection.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isNoContent());

        // Consulta subsequente por ID retorna 404 (soft deleted)
        mockMvc.perform(get("/api/v1/inspections/" + inspection.id())
                        .header("Authorization", ctx.token()))
                .andExpect(status().isNotFound());
    }
}
