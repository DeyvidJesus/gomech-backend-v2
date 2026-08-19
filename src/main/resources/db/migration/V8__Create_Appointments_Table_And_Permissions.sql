-- ==============================================================================
-- V8: Create Appointments Table, Indexes, RLS Policies and Seed Permissions
-- ==============================================================================

CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    estimated_end_at TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    service_type VARCHAR(100),
    notes TEXT,
    cancellation_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_appointments_tenant_unit_scheduled ON appointments(tenant_id, unit_id, scheduled_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_appointments_tenant_status ON appointments(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_appointments_customer ON appointments(tenant_id, customer_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_appointments_vehicle ON appointments(tenant_id, vehicle_id) WHERE deleted_at IS NULL;

-- Enable Row Level Security (RLS)
ALTER TABLE appointments ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_unit_isolation_policy ON appointments
    FOR ALL
    USING (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR current_setting('app.current_unit', true) IS NULL
            OR current_setting('app.current_unit', true) = ''
            OR unit_id = current_setting('app.current_unit', true)::uuid
        )
    )
    WITH CHECK (
        tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid
        AND (
            unit_id IS NULL
            OR current_setting('app.current_unit', true) IS NULL
            OR current_setting('app.current_unit', true) = ''
            OR unit_id = current_setting('app.current_unit', true)::uuid
        )
    );

-- Seed System Permissions for Operations Appointments
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'OPERATIONS_APPOINTMENT_READ', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_APPOINTMENT_WRITE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_APPOINTMENT_CANCEL', 'OPERATIONS')
ON CONFLICT (code) DO NOTHING;
