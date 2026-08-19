package com.gomech.api.modules.crm.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.crm.api.dto.CreateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.CustomerResponse;
import com.gomech.api.modules.crm.api.dto.CustomerSummaryResponse;
import com.gomech.api.modules.crm.api.dto.UpdateCustomerRequest;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.crm.domain.CpfCnpjValidator;
import com.gomech.api.modules.crm.domain.CustomerNotFoundException;
import com.gomech.api.modules.crm.domain.DuplicateDocumentException;
import com.gomech.api.modules.crm.domain.InvalidDocumentException;
import com.gomech.api.modules.crm.domain.LicensePlateValidator;
import com.gomech.api.modules.crm.events.CustomerCreatedEvent;
import com.gomech.api.modules.crm.events.CustomerDeletedEvent;
import com.gomech.api.modules.crm.events.CustomerUpdatedEvent;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Customer;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Vehicle;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.CustomerRepository;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.VehicleRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final VehicleRepository vehicleRepository;
    private final DomainEventBus domainEventBus;

    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        String normalizedDoc = CpfCnpjValidator.normalize(request.document());
        if (normalizedDoc != null) {
            if (!CpfCnpjValidator.isValid(normalizedDoc)) {
                throw new InvalidDocumentException(request.document());
            }
            if (customerRepository.existsByTenantIdAndDocumentAndDeletedAtIsNull(effectiveTenantId, normalizedDoc)) {
                throw new DuplicateDocumentException(request.document());
            }
        }

        Customer customer = new Customer();
        customer.setTenantId(effectiveTenantId);
        customer.setName(request.name().trim());
        customer.setDocument(normalizedDoc);
        customer.setPhone(request.phone() != null ? request.phone().trim() : null);
        customer.setEmail(request.email() != null ? request.email().toLowerCase().trim() : null);
        customer.setAddress(request.address() != null ? request.address().trim() : null);

        Customer saved = customerRepository.save(customer);
        log.info("Cliente {} ({}) criado com sucesso para o tenant {}", saved.getName(), saved.getId(), effectiveTenantId);

        domainEventBus.publish(new CustomerCreatedEvent(effectiveTenantId, saved.getId(), saved.getName(), saved.getDocument()));

        return toCustomerResponse(saved, List.of());
    }

    @Transactional
    public CustomerResponse updateCustomer(UUID id, UpdateCustomerRequest request, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Customer customer = customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        String normalizedDoc = CpfCnpjValidator.normalize(request.document());
        if (normalizedDoc != null) {
            if (!CpfCnpjValidator.isValid(normalizedDoc)) {
                throw new InvalidDocumentException(request.document());
            }
            if (customerRepository.existsByTenantIdAndDocumentAndIdNotAndDeletedAtIsNull(effectiveTenantId, normalizedDoc, id)) {
                throw new DuplicateDocumentException(request.document());
            }
        }

        customer.setName(request.name().trim());
        customer.setDocument(normalizedDoc);
        customer.setPhone(request.phone() != null ? request.phone().trim() : null);
        customer.setEmail(request.email() != null ? request.email().toLowerCase().trim() : null);
        customer.setAddress(request.address() != null ? request.address().trim() : null);

        Customer updated = customerRepository.save(customer);
        log.info("Cliente {} ({}) atualizado com sucesso no tenant {}", updated.getName(), updated.getId(), effectiveTenantId);

        domainEventBus.publish(new CustomerUpdatedEvent(effectiveTenantId, updated.getId(), updated.getName()));

        List<Vehicle> vehicles = vehicleRepository.findByCustomerIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId);
        return toCustomerResponse(updated, vehicles);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomerById(UUID id, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Customer customer = customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        List<Vehicle> vehicles = vehicleRepository.findByCustomerIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId);
        return toCustomerResponse(customer, vehicles);
    }

    @Transactional(readOnly = true)
    public Page<CustomerSummaryResponse> searchCustomers(
            String q,
            String name,
            String document,
            String phone,
            String email,
            Pageable pageable,
            UUID tenantId
    ) {
        UUID effectiveTenantId = resolveTenantId(tenantId);
        String normalizedDoc = CpfCnpjValidator.normalize(document);
        String cleanQ = (q != null && !q.isBlank()) ? q.trim() : null;
        String cleanName = (name != null && !name.isBlank()) ? name.trim() : null;
        String cleanPhone = (phone != null && !phone.isBlank()) ? phone.trim() : null;
        String cleanEmail = (email != null && !email.isBlank()) ? email.trim() : null;

        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), effectiveTenantId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (cleanQ != null) {
                String pattern = "%" + cleanQ.toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), pattern);
                Predicate docLike = cb.like(root.get("document"), "%" + cleanQ + "%");
                Predicate phoneLike = cb.like(root.get("phone"), "%" + cleanQ + "%");
                Predicate emailLike = cb.like(cb.lower(root.get("email")), pattern);
                predicates.add(cb.or(nameLike, docLike, phoneLike, emailLike));
            }
            if (cleanName != null) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + cleanName.toLowerCase() + "%"));
            }
            if (normalizedDoc != null) {
                predicates.add(cb.equal(root.get("document"), normalizedDoc));
            }
            if (cleanPhone != null) {
                predicates.add(cb.like(root.get("phone"), "%" + cleanPhone + "%"));
            }
            if (cleanEmail != null) {
                predicates.add(cb.equal(cb.lower(root.get("email")), cleanEmail.toLowerCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Customer> page = customerRepository.findAll(spec, pageable);
        return page.map(this::toSummaryResponse);
    }

    @Transactional
    public void deleteCustomer(UUID id, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Customer customer = customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new CustomerNotFoundException(id));

        OffsetDateTime now = OffsetDateTime.now();
        customer.setDeletedAt(now);
        customerRepository.save(customer);

        // Soft delete cascateado para os veículos associados ao cliente
        List<Vehicle> vehicles = vehicleRepository.findByCustomerIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId);
        for (Vehicle vehicle : vehicles) {
            vehicle.setDeletedAt(now);
            vehicleRepository.save(vehicle);
        }

        log.info("Cliente {} e {} veículo(s) associado(s) desativados com sucesso no tenant {}", id, vehicles.size(), effectiveTenantId);
        domainEventBus.publish(new CustomerDeletedEvent(effectiveTenantId, id));
    }

    private CustomerResponse toCustomerResponse(Customer customer, List<Vehicle> vehicles) {
        List<VehicleSummaryResponse> vehicleSummaries = vehicles.stream()
                .map(v -> new VehicleSummaryResponse(
                        v.getId(),
                        customer.getId(),
                        customer.getName(),
                        v.getLicensePlate(),
                        LicensePlateValidator.format(v.getLicensePlate()),
                        v.getBrand(),
                        v.getModel(),
                        v.getYear(),
                        v.getCurrentMileage()
                ))
                .toList();

        return new CustomerResponse(
                customer.getId(),
                customer.getTenantId(),
                customer.getName(),
                customer.getDocument(),
                CpfCnpjValidator.format(customer.getDocument()),
                customer.getPhone(),
                customer.getEmail(),
                customer.getAddress(),
                vehicleSummaries,
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }

    private CustomerSummaryResponse toSummaryResponse(Customer customer) {
        int vehicleCount = customer.getVehicles() != null ? (int) customer.getVehicles().stream().filter(v -> v.getDeletedAt() == null).count() : 0;
        return new CustomerSummaryResponse(
                customer.getId(),
                customer.getName(),
                customer.getDocument(),
                CpfCnpjValidator.format(customer.getDocument()),
                customer.getPhone(),
                customer.getEmail(),
                vehicleCount,
                customer.getCreatedAt()
        );
    }

    private UUID resolveTenantId(UUID tenantId) {
        return tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    }
}
