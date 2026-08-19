package com.gomech.api.modules.operations.infrastructure.persistence.model;

import com.gomech.api.modules.operations.domain.CustomerApprovalStatus;
import com.gomech.api.modules.operations.domain.QuoteStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.TenantId;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "quotes")
@SQLDelete(sql = "UPDATE quotes SET deleted_at = NOW() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Quote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "inspection_id")
    private UUID inspectionId;

    @Column(name = "appointment_id")
    private UUID appointmentId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private QuoteStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_approval_status", nullable = false, length = 50)
    private CustomerApprovalStatus customerApprovalStatus;

    @Column(name = "customer_decision_at")
    private OffsetDateTime customerDecisionAt;

    @Column(name = "customer_decision_notes", columnDefinition = "TEXT")
    private String customerDecisionNotes;

    @Column(name = "subtotal_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_labor_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLaborAmount;

    @Column(name = "total_parts_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPartsAmount;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<QuoteItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public Quote() {
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Quote(UUID tenantId, UUID unitId, UUID customerId, UUID vehicleId, UUID inspectionId,
                 UUID appointmentId, UUID createdByUserId, OffsetDateTime validUntil,
                 String notes, String termsAndConditions) {
        this.tenantId = tenantId;
        this.unitId = unitId;
        this.customerId = customerId;
        this.vehicleId = vehicleId;
        this.inspectionId = inspectionId;
        this.appointmentId = appointmentId;
        this.createdByUserId = createdByUserId;
        this.status = QuoteStatus.DRAFT;
        this.customerApprovalStatus = CustomerApprovalStatus.PENDING;
        this.subtotalAmount = BigDecimal.ZERO;
        this.discountAmount = BigDecimal.ZERO;
        this.taxAmount = BigDecimal.ZERO;
        this.totalLaborAmount = BigDecimal.ZERO;
        this.totalPartsAmount = BigDecimal.ZERO;
        this.totalAmount = BigDecimal.ZERO;
        this.validUntil = validUntil;
        this.notes = notes;
        this.termsAndConditions = termsAndConditions;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public void addItem(QuoteItem item) {
        items.add(item);
        item.setQuote(this);
    }

    public void removeItem(QuoteItem item) {
        items.remove(item);
        item.setQuote(null);
    }

    public void clearItems() {
        for (QuoteItem item : items) {
            item.setQuote(null);
        }
        items.clear();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public void setUnitId(UUID unitId) {
        this.unitId = unitId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public UUID getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(UUID vehicleId) {
        this.vehicleId = vehicleId;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public void setInspectionId(UUID inspectionId) {
        this.inspectionId = inspectionId;
    }

    public UUID getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(UUID appointmentId) {
        this.appointmentId = appointmentId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(UUID createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public UUID getApprovedByUserId() {
        return approvedByUserId;
    }

    public void setApprovedByUserId(UUID approvedByUserId) {
        this.approvedByUserId = approvedByUserId;
    }

    public OffsetDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(OffsetDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    public void setStatus(QuoteStatus status) {
        this.status = status;
    }

    public CustomerApprovalStatus getCustomerApprovalStatus() {
        return customerApprovalStatus;
    }

    public void setCustomerApprovalStatus(CustomerApprovalStatus customerApprovalStatus) {
        this.customerApprovalStatus = customerApprovalStatus;
    }

    public OffsetDateTime getCustomerDecisionAt() {
        return customerDecisionAt;
    }

    public void setCustomerDecisionAt(OffsetDateTime customerDecisionAt) {
        this.customerDecisionAt = customerDecisionAt;
    }

    public String getCustomerDecisionNotes() {
        return customerDecisionNotes;
    }

    public void setCustomerDecisionNotes(String customerDecisionNotes) {
        this.customerDecisionNotes = customerDecisionNotes;
    }

    public BigDecimal getSubtotalAmount() {
        return subtotalAmount;
    }

    public void setSubtotalAmount(BigDecimal subtotalAmount) {
        this.subtotalAmount = subtotalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getTaxAmount() {
        return taxAmount;
    }

    public void setTaxAmount(BigDecimal taxAmount) {
        this.taxAmount = taxAmount;
    }

    public BigDecimal getTotalLaborAmount() {
        return totalLaborAmount;
    }

    public void setTotalLaborAmount(BigDecimal totalLaborAmount) {
        this.totalLaborAmount = totalLaborAmount;
    }

    public BigDecimal getTotalPartsAmount() {
        return totalPartsAmount;
    }

    public void setTotalPartsAmount(BigDecimal totalPartsAmount) {
        this.totalPartsAmount = totalPartsAmount;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    public void setTermsAndConditions(String termsAndConditions) {
        this.termsAndConditions = termsAndConditions;
    }

    public List<QuoteItem> getItems() {
        return items;
    }

    public void setItems(List<QuoteItem> items) {
        this.items = items;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public Long getVersion() {
        return version;
    }
}
