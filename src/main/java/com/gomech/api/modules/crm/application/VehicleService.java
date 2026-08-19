package com.gomech.api.modules.crm.application;

import com.gomech.api.core.events.DomainEventBus;
import com.gomech.api.core.tenancy.TenantContextHolder;
import com.gomech.api.modules.crm.api.dto.CreateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.UpdateVehicleRequest;
import com.gomech.api.modules.crm.api.dto.VehicleResponse;
import com.gomech.api.modules.crm.api.dto.VehicleSummaryResponse;
import com.gomech.api.modules.crm.domain.CustomerNotFoundException;
import com.gomech.api.modules.crm.domain.DuplicateLicensePlateException;
import com.gomech.api.modules.crm.domain.InvalidLicensePlateException;
import com.gomech.api.modules.crm.domain.LicensePlateValidator;
import com.gomech.api.modules.crm.domain.VehicleNotFoundException;
import com.gomech.api.modules.crm.events.VehicleCreatedEvent;
import com.gomech.api.modules.crm.events.VehicleDeletedEvent;
import com.gomech.api.modules.crm.events.VehicleUpdatedEvent;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Customer;
import com.gomech.api.modules.crm.infrastructure.persistence.model.Vehicle;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.CustomerRepository;
import com.gomech.api.modules.crm.infrastructure.persistence.repository.VehicleRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
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
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final CustomerRepository customerRepository;
    private final DomainEventBus domainEventBus;

    @Transactional
    public VehicleResponse createVehicle(CreateVehicleRequest request, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        String normalizedPlate = LicensePlateValidator.normalize(request.licensePlate());
        if (!LicensePlateValidator.isValid(normalizedPlate)) {
            throw new InvalidLicensePlateException(request.licensePlate());
        }

        if (vehicleRepository.existsByTenantIdAndLicensePlateAndDeletedAtIsNull(effectiveTenantId, normalizedPlate)) {
            throw new DuplicateLicensePlateException(request.licensePlate());
        }

        // Validação de isolamento de escopo: o cliente deve existir e pertencer ao mesmo tenant
        Customer customer = customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.customerId(), effectiveTenantId)
                .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));

        Vehicle vehicle = new Vehicle();
        vehicle.setTenantId(effectiveTenantId);
        vehicle.setCustomer(customer);
        vehicle.setLicensePlate(normalizedPlate);
        vehicle.setBrand(request.brand() != null ? request.brand().trim() : null);
        vehicle.setModel(request.model() != null ? request.model().trim() : null);
        vehicle.setYear(request.year());
        vehicle.setVin(request.vin() != null ? request.vin().toUpperCase().trim() : null);
        vehicle.setCurrentMileage(request.currentMileage());

        Vehicle saved = vehicleRepository.save(vehicle);
        log.info("Veículo placa {} ({}) cadastrado com sucesso para o cliente {} no tenant {}",
                saved.getLicensePlate(), saved.getId(), customer.getId(), effectiveTenantId);

        domainEventBus.publish(new VehicleCreatedEvent(effectiveTenantId, saved.getId(), customer.getId(), saved.getLicensePlate()));

        return toVehicleResponse(saved);
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID id, UpdateVehicleRequest request, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new VehicleNotFoundException(id));

        String normalizedPlate = LicensePlateValidator.normalize(request.licensePlate());
        if (!LicensePlateValidator.isValid(normalizedPlate)) {
            throw new InvalidLicensePlateException(request.licensePlate());
        }

        if (vehicleRepository.existsByTenantIdAndLicensePlateAndIdNotAndDeletedAtIsNull(effectiveTenantId, normalizedPlate, id)) {
            throw new DuplicateLicensePlateException(request.licensePlate());
        }

        // Se o cliente associado foi alterado, validar que pertence ao mesmo tenant
        if (request.customerId() != null && !request.customerId().equals(vehicle.getCustomer().getId())) {
            Customer newCustomer = customerRepository.findByIdAndTenantIdAndDeletedAtIsNull(request.customerId(), effectiveTenantId)
                    .orElseThrow(() -> new CustomerNotFoundException(request.customerId()));
            vehicle.setCustomer(newCustomer);
        }

        vehicle.setLicensePlate(normalizedPlate);
        vehicle.setBrand(request.brand() != null ? request.brand().trim() : null);
        vehicle.setModel(request.model() != null ? request.model().trim() : null);
        vehicle.setYear(request.year());
        vehicle.setVin(request.vin() != null ? request.vin().toUpperCase().trim() : null);
        vehicle.setCurrentMileage(request.currentMileage());

        Vehicle updated = vehicleRepository.save(vehicle);
        log.info("Veículo placa {} ({}) atualizado com sucesso no tenant {}",
                updated.getLicensePlate(), updated.getId(), effectiveTenantId);

        domainEventBus.publish(new VehicleUpdatedEvent(effectiveTenantId, updated.getId(), updated.getLicensePlate()));

        return toVehicleResponse(updated);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicleById(UUID id, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new VehicleNotFoundException(id));

        return toVehicleResponse(vehicle);
    }

    @Transactional(readOnly = true)
    public Page<VehicleSummaryResponse> searchVehicles(
            String q,
            String licensePlate,
            String brand,
            String model,
            UUID customerId,
            Pageable pageable,
            UUID tenantId
    ) {
        UUID effectiveTenantId = resolveTenantId(tenantId);
        String normalizedPlate = LicensePlateValidator.normalize(licensePlate);
        String cleanQ = (q != null && !q.isBlank()) ? q.trim() : null;
        String cleanBrand = (brand != null && !brand.isBlank()) ? brand.trim() : null;
        String cleanModel = (model != null && !model.isBlank()) ? model.trim() : null;

        Specification<Vehicle> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), effectiveTenantId));
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("customer", JoinType.LEFT);
            }
            Join<Vehicle, Customer> customerJoin = root.join("customer", JoinType.LEFT);

            if (cleanQ != null) {
                String upperPattern = "%" + cleanQ.toUpperCase() + "%";
                String lowerPattern = "%" + cleanQ.toLowerCase() + "%";
                Predicate plateLike = cb.like(root.get("licensePlate"), upperPattern);
                Predicate brandLike = cb.like(cb.lower(root.get("brand")), lowerPattern);
                Predicate modelLike = cb.like(cb.lower(root.get("model")), lowerPattern);
                Predicate vinLike = cb.like(root.get("vin"), upperPattern);
                Predicate custLike = cb.like(cb.lower(customerJoin.get("name")), lowerPattern);
                predicates.add(cb.or(plateLike, brandLike, modelLike, vinLike, custLike));
            }
            if (normalizedPlate != null) {
                predicates.add(cb.equal(root.get("licensePlate"), normalizedPlate));
            }
            if (cleanBrand != null) {
                predicates.add(cb.like(cb.lower(root.get("brand")), "%" + cleanBrand.toLowerCase() + "%"));
            }
            if (cleanModel != null) {
                predicates.add(cb.like(cb.lower(root.get("model")), "%" + cleanModel.toLowerCase() + "%"));
            }
            if (customerId != null) {
                predicates.add(cb.equal(customerJoin.get("id"), customerId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Vehicle> page = vehicleRepository.findAll(spec, pageable);
        return page.map(this::toSummaryResponse);
    }

    @Transactional
    public void deleteVehicle(UUID id, UUID tenantId) {
        UUID effectiveTenantId = resolveTenantId(tenantId);

        Vehicle vehicle = vehicleRepository.findByIdAndTenantIdAndDeletedAtIsNull(id, effectiveTenantId)
                .orElseThrow(() -> new VehicleNotFoundException(id));

        vehicle.setDeletedAt(OffsetDateTime.now());
        vehicleRepository.save(vehicle);

        log.info("Veículo placa {} ({}) desativado com sucesso no tenant {}",
                vehicle.getLicensePlate(), id, effectiveTenantId);

        domainEventBus.publish(new VehicleDeletedEvent(effectiveTenantId, id));
    }

    private VehicleResponse toVehicleResponse(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getTenantId(),
                vehicle.getCustomer().getId(),
                vehicle.getCustomer().getName(),
                vehicle.getLicensePlate(),
                LicensePlateValidator.format(vehicle.getLicensePlate()),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getVin(),
                vehicle.getCurrentMileage(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }

    private VehicleSummaryResponse toSummaryResponse(Vehicle vehicle) {
        return new VehicleSummaryResponse(
                vehicle.getId(),
                vehicle.getCustomer().getId(),
                vehicle.getCustomer().getName(),
                vehicle.getLicensePlate(),
                LicensePlateValidator.format(vehicle.getLicensePlate()),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getCurrentMileage()
        );
    }

    private UUID resolveTenantId(UUID tenantId) {
        return tenantId != null ? tenantId : TenantContextHolder.getTenantId();
    }
}
