package com.gomech.api.modules.crm.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.modules.crm.api.dto.CreateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.CustomerResponse;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.UpdateCustomerRequest;
import com.gomech.api.modules.crm.domain.CustomerNotFoundException;
import com.gomech.api.modules.crm.domain.DuplicateDocumentException;
import com.gomech.api.modules.crm.domain.InvalidDocumentException;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Customer;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Vehicle;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.CustomerRepository;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private DomainEventBus domainEventBus;

    private CustomerService customerService;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(customerRepository, vehicleRepository, domainEventBus);
    }

    @Test
    @DisplayName("Deve cadastrar cliente com CPF válido e publicar evento")
    void shouldCreateCustomerWithValidCpf() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "João da Silva",
                "529.982.247-25",
                "(11) 98888-7777",
                "joao@silva.com",
                "Rua A, 123"
        );

        when(customerRepository.existsByTenantIdAndDocumentAndDeletedAtIsNull(eq(tenantId), eq("52998224725"))).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(OffsetDateTime.now());
            c.setUpdatedAt(OffsetDateTime.now());
            return c;
        });

        CustomerResponse response = customerService.createCustomer(request, tenantId);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("João da Silva");
        assertThat(response.document()).isEqualTo("52998224725");
        assertThat(response.formattedDocument()).isEqualTo("529.982.247-25");
        verify(domainEventBus).publish(any());
    }

    @Test
    @DisplayName("Deve rejeitar cadastro com CPF inválido")
    void shouldRejectCustomerWithInvalidCpf() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "João Inválido",
                "123.456.789-99",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> customerService.createCustomer(request, tenantId))
                .isInstanceOf(InvalidDocumentException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve rejeitar cadastro com documento duplicado no mesmo tenant")
    void shouldRejectDuplicateDocumentInSameTenant() {
        CreateCustomerRequest request = new CreateCustomerRequest(
                "Maria Souza",
                "52998224725",
                null,
                null,
                null
        );

        when(customerRepository.existsByTenantIdAndDocumentAndDeletedAtIsNull(eq(tenantId), eq("52998224725"))).thenReturn(true);

        assertThatThrownBy(() -> customerService.createCustomer(request, tenantId))
                .isInstanceOf(DuplicateDocumentException.class);
        verify(customerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Deve atualizar dados do cliente e buscar veículos vinculados")
    void shouldUpdateCustomer() {
        UUID customerId = UUID.randomUUID();
        Customer existing = new Customer();
        existing.setId(customerId);
        existing.setTenantId(tenantId);
        existing.setName("Carlos");
        existing.setDocument("52998224725");

        UpdateCustomerRequest updateReq = new UpdateCustomerRequest(
                "Carlos Alberto",
                "52998224725",
                "(11) 99999-0000",
                "carlos@novo.com",
                "Av. Nova, 500"
        );

        when(customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vehicleRepository.findByCustomerIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)).thenReturn(List.of());

        CustomerResponse response = customerService.updateCustomer(customerId, updateReq, tenantId);

        assertThat(response.name()).isEqualTo("Carlos Alberto");
        assertThat(response.phone()).isEqualTo("(11) 99999-0000");
        verify(domainEventBus).publish(any());
    }

    @Test
    @DisplayName("Deve realizar soft delete no cliente e cascatear para veículos")
    void shouldSoftDeleteCustomerAndLinkedVehicles() {
        UUID customerId = UUID.randomUUID();
        Customer existing = new Customer();
        existing.setId(customerId);
        existing.setTenantId(tenantId);

        Vehicle v1 = new Vehicle();
        v1.setId(UUID.randomUUID());
        v1.setTenantId(tenantId);

        when(customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)).thenReturn(Optional.of(existing));
        when(vehicleRepository.findByCustomerIdAndTenantIdAndDeletedAtIsNull(customerId, tenantId)).thenReturn(List.of(v1));

        customerService.deleteCustomer(customerId, tenantId);

        assertThat(existing.getDeletedAt()).isNotNull();
        assertThat(v1.getDeletedAt()).isNotNull();
        verify(customerRepository).save(existing);
        verify(vehicleRepository).save(v1);
        verify(domainEventBus).publish(any());
    }

    @Test
    @DisplayName("Deve buscar clientes paginados")
    void shouldSearchCustomersWithPagination() {
        Customer c1 = new Customer();
        c1.setId(UUID.randomUUID());
        c1.setName("Ana Silva");
        c1.setCreatedAt(OffsetDateTime.now());

        Page<Customer> page = new PageImpl<>(List.of(c1), PageRequest.of(0, 10), 1);
        when(customerRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<CustomerSummaryResponse> result = customerService.searchCustomers("Ana", null, null, null, null, PageRequest.of(0, 10), tenantId);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Ana Silva");
    }
}
