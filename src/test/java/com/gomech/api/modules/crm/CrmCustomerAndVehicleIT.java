package com.gomech.api.modules.crm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gomech.api.modules.crm.api.dto.CreateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.CreateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.CustomerResponse;
import com.gomech.api.modules.crm.api.dto.UpdateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.VehicleResponse;
import com.gomech.api.modules.iam.api.dto.AuthResponse;
import com.gomech.api.modules.iam.api.dto.RegisterWorkshopRequest;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class CrmCustomerAndVehicleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String registerWorkshopAndGetToken(String prefix) throws Exception {
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
        return "Bearer " + auth.accessToken();
    }

    @Test
    @DisplayName("Deve cadastrar cliente com CPF e consultar detalhes e listagem paginada")
    void shouldCreateCustomerAndSearch() throws Exception {
        String token = registerWorkshopAndGetToken("crm-search");

        // 1. Cadastrar cliente
        CreateCustomerRequest createReq = new CreateCustomerRequest(
                "Carlos Henrique",
                "529.982.247-25",
                "(11) 98765-4321",
                "carlos.henrique@email.com",
                "Rua das Flores, 123"
        );

        String createJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Carlos Henrique"))
                .andExpect(jsonPath("$.document").value("52998224725"))
                .andExpect(jsonPath("$.formattedDocument").value("529.982.247-25"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        CustomerResponse customer = objectMapper.readValue(createJson, CustomerResponse.class);

        // 2. Consultar cliente por ID
        mockMvc.perform(get("/api/v1/customers/" + customer.id())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer.id().toString()))
                .andExpect(jsonPath("$.name").value("Carlos Henrique"));

        // 3. Buscar com busca paginada
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", token)
                        .param("q", "Henrique")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].name").value("Carlos Henrique"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(10));
    }

    @Test
    @DisplayName("Deve rejeitar cliente com documento inválido ou duplicado")
    void shouldRejectInvalidOrDuplicateDocument() throws Exception {
        String token = registerWorkshopAndGetToken("crm-doc-val");

        // Documento com dígito inválido -> 422
        CreateCustomerRequest invalidDocReq = new CreateCustomerRequest(
                "Cliente Invalido",
                "123.456.789-00",
                null,
                null,
                null
        );
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDocReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Invalid Document"));

        // Criar cliente válido
        CreateCustomerRequest validReq = new CreateCustomerRequest(
                "Cliente Valido",
                "52998224725",
                null,
                null,
                null
        );
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isCreated());

        // Tentar criar outro com mesmo CPF -> 409 Conflict
        mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Duplicate Document"));
    }

    @Test
    @DisplayName("Deve cadastrar veículo, associar ao cliente e consultar em cascata")
    void shouldCreateVehicleAndAssociateWithCustomer() throws Exception {
        String token = registerWorkshopAndGetToken("crm-vehicle");

        // 1. Cadastrar cliente
        CreateCustomerRequest custReq = new CreateCustomerRequest("Mariana Lima", null, "(21) 97777-6666", null, null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        // 2. Cadastrar veículo Mercosul
        CreateVehicleRequest vehReq = new CreateVehicleRequest(
                customer.id(),
                "bra2e19",
                "Honda",
                "Civic Touring 1.5",
                2022,
                "93HGK2680NZ00001",
                28000
        );

        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("BRA2E19"))
                .andExpect(jsonPath("$.customerName").value("Mariana Lima"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 3. Consultar cliente e verificar veículo na lista
        mockMvc.perform(get("/api/v1/customers/" + customer.id())
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicles[0].id").value(vehicle.id().toString()))
                .andExpect(jsonPath("$.vehicles[0].licensePlate").value("BRA2E19"));

        // 4. Buscar veículos com filtro de placa
        mockMvc.perform(get("/api/v1/vehicles")
                        .header("Authorization", token)
                        .param("licensePlate", "BRA2E19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].model").value("Civic Touring 1.5"));
    }

    @Test
    @DisplayName("Deve rejeitar criação de veículo apontando para cliente de outro tenant")
    void shouldRejectCrossTenantVehicleCreation() throws Exception {
        String tokenA = registerWorkshopAndGetToken("crm-tenant-a");
        String tokenB = registerWorkshopAndGetToken("crm-tenant-b");

        // Criar cliente no Tenant A
        CreateCustomerRequest custReqA = new CreateCustomerRequest("Cliente A", null, null, null, null);
        String custJsonA = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReqA)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        CustomerResponse customerA = objectMapper.readValue(custJsonA, CustomerResponse.class);

        // Tenant B tenta cadastrar veículo apontando para o cliente do Tenant A
        CreateVehicleRequest vehReqB = new CreateVehicleRequest(
                customerA.id(),
                "ABC-1234",
                "Fiat",
                "Uno",
                2015,
                null,
                90000
        );

        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReqB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Customer Not Found"));
    }

    @Test
    @DisplayName("Deve validar unicidade de placa e permitir re-cadastro após soft delete")
    void shouldEnforcePlateUniquenessAndAllowReRegistrationAfterSoftDelete() throws Exception {
        String token = registerWorkshopAndGetToken("crm-soft-delete");

        // 1. Criar cliente
        CreateCustomerRequest custReq = new CreateCustomerRequest("Roberto", null, null, null, null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        // 2. Criar veículo
        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "ABC-9999", "Ford", "Ka", 2018, null, 60000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 3. Tentar criar outro com a mesma placa -> 409
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isConflict());

        // 4. Soft delete do veículo -> 204
        mockMvc.perform(delete("/api/v1/vehicles/" + vehicle.id())
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        // 5. Consultar por ID -> 404
        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());

        // 6. Cadastrar novamente a mesma placa após desativação -> 201 Created
        mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.licensePlate").value("ABC9999"));
    }

    @Test
    @DisplayName("Deve desativar cliente e cascatear soft delete para seus veículos")
    void shouldSoftDeleteCustomerAndCascadeToVehicles() throws Exception {
        String token = registerWorkshopAndGetToken("crm-cust-delete");

        // 1. Criar cliente e veículo
        CreateCustomerRequest custReq = new CreateCustomerRequest("Fernanda", null, null, null, null);
        String custJson = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(custReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        CustomerResponse customer = objectMapper.readValue(custJson, CustomerResponse.class);

        CreateVehicleRequest vehReq = new CreateVehicleRequest(customer.id(), "XYZ-1234", "Fiat", "Argo", 2020, null, 30000);
        String vehJson = mockMvc.perform(post("/api/v1/vehicles")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vehReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        VehicleResponse vehicle = objectMapper.readValue(vehJson, VehicleResponse.class);

        // 2. Soft delete do cliente
        mockMvc.perform(delete("/api/v1/customers/" + customer.id())
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        // 3. Cliente e veículo devem retornar 404
        mockMvc.perform(get("/api/v1/customers/" + customer.id())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/vehicles/" + vehicle.id())
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}
