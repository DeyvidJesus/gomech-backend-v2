package com.gomech.api.modules.crm.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.dto.CreateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.UpdateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.VehicleResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.crm.domain.CustomerNotFoundException;
import com.gomech.api.modules.crm.domain.DuplicateLicensePlateException;
import com.gomech.api.modules.crm.domain.InvalidLicensePlateException;
import com.gomech.api.modules.crm.domain.VehicleNotFoundException;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Customer;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Vehicle;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.CustomerRepository;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private DomainEventBus domainEventBus;

    private VehicleService vehicleService;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        vehicleService = new VehicleService(vehicleRepository, customerRepository, domainEventBus);
    }

    @Test
    @DisplayName("Deve cadastrar veículo vinculado ao cliente com sucesso")
    void shouldCreateVehicleSuccessfully() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setName("João Dono");
        customer.setTenantId(tenantId);

        CreateVehicleRequest request = new CreateVehicleRequest(
                customerId,
                "BRA2E19",
                "Volkswagen",
                "Golf GTI",
                2021,
                "9BWAA01JX00001",
                42000
        );

        when(vehicleRepository.existsByTenantIdAndLicensePlateAndDeletedAtIsNull(tenantId, "BRA2E19")).thenReturn(false);
        when(customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)).thenReturn(Optional.of(customer));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(inv -> {
            Vehicle v = inv.getArgument(0);
            v.setId(UUID.randomUUID());
            v.setCreatedAt(OffsetDateTime.now());
            v.setUpdatedAt(OffsetDateTime.now());
            return v;
        });

        VehicleResponse response = vehicleService.createVehicle(request, tenantId);

        assertThat(response.id()).isNotNull();
        assertThat(response.licensePlate()).isEqualTo("BRA2E19");
        assertThat(response.customerName()).isEqualTo("João Dono");
        verify(domainEventBus).publish(any());
    }

    @Test
    @DisplayName("Deve rejeitar veículo com formato de placa inválido")
    void shouldRejectInvalidLicensePlateFormat() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                UUID.randomUUID(),
                "INVALID123",
                "Fiat",
                "Uno",
                2010,
                null,
                100000
        );

        assertThatThrownBy(() -> vehicleService.createVehicle(request, tenantId))
                .isInstanceOf(InvalidLicensePlateException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar veículo com placa duplicada no mesmo tenant")
    void shouldRejectDuplicateLicensePlateInSameTenant() {
        CreateVehicleRequest request = new CreateVehicleRequest(
                UUID.randomUUID(),
                "ABC-1234",
                "Fiat",
                "Uno",
                2010,
                null,
                100000
        );

        when(vehicleRepository.existsByTenantIdAndLicensePlateAndDeletedAtIsNull(tenantId, "ABC1234")).thenReturn(true);

        assertThatThrownBy(() -> vehicleService.createVehicle(request, tenantId))
                .isInstanceOf(DuplicateLicensePlateException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar vínculo com cliente inexistente ou de outro tenant")
    void shouldRejectCrossTenantCustomerAssociation() {
        UUID crossTenantCustomerId = UUID.randomUUID();
        CreateVehicleRequest request = new CreateVehicleRequest(
                crossTenantCustomerId,
                "ABC-1234",
                "Fiat",
                "Palio",
                2015,
                null,
                50000
        );

        when(vehicleRepository.existsByTenantIdAndLicensePlateAndDeletedAtIsNull(tenantId, "ABC1234")).thenReturn(false);
        when(customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(crossTenantCustomerId, tenantId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> vehicleService.createVehicle(request, tenantId))
                .isInstanceOf(CustomerNotFoundException.class);
        verify(vehicleRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve realizar soft delete no veículo")
    void shouldSoftDeleteVehicle() {
        UUID vehicleId = UUID.randomUUID();
        Vehicle vehicle = new Vehicle();
        vehicle.setId(vehicleId);
        vehicle.setTenantId(tenantId);
        vehicle.setLicensePlate("ABC1234");

        when(vehicleRepository.findByIdAndTenantIdAndDeletedAtIsNull(vehicleId, tenantId)).thenReturn(Optional.of(vehicle));

        vehicleService.deleteVehicle(vehicleId, tenantId);

        assertThat(vehicle.getDeletedAt()).isNotNull();
        verify(vehicleRepository).save(vehicle);
        verify(domainEventBus).publish(any());
    }

    @Test
    @DisplayName("Deve buscar veículos paginados com filtro de placa")
    void shouldSearchVehiclesWithPagination() {
        Customer c = new Customer();
        c.setId(UUID.randomUUID());
        c.setName("Maria");

        Vehicle v = new Vehicle();
        v.setId(UUID.randomUUID());
        v.setCustomer(c);
        v.setLicensePlate("BRA2E19");
        v.setBrand("VW");
        v.setModel("Gol");

        Page<Vehicle> page = new PageImpl<>(List.of(v), PageRequest.of(0, 10), 1);
        when(vehicleRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<VehicleSummaryResponse> result = vehicleService.searchVehicles(null, "BRA2E19", null, null, null, PageRequest.of(0, 10), tenantId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).licensePlate()).isEqualTo("BRA2E19");
    }
}
