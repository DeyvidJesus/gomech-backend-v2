-- ==============================================================================
-- V9: Create Inspections and Inspection Items Tables, Indexes, RLS and Permissions
-- ==============================================================================

CREATE TABLE inspections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    customer_id UUID NOT NULL REFERENCES customers(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    appointment_id UUID REFERENCES appointments(id),
    inspector_user_id UUID REFERENCES users(id),
    status VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    fuel_level VARCHAR(50),
    current_mileage INTEGER,
    general_notes TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_inspections_tenant_vehicle ON inspections(tenant_id, vehicle_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_inspections_tenant_unit ON inspections(tenant_id, unit_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_inspections_tenant_status ON inspections(tenant_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_inspections_appointment ON inspections(tenant_id, appointment_id) WHERE deleted_at IS NULL;

CREATE TABLE inspection_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    inspection_id UUID NOT NULL REFERENCES inspections(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    category VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OK',
    notes TEXT,
    recommended_action TEXT,
    photo_urls TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_inspection_items_inspection ON inspection_items(tenant_id, inspection_id);

-- Enable Row Level Security (RLS)
ALTER TABLE inspections ENABLE ROW LEVEL SECURITY;
ALTER TABLE inspection_items ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_unit_isolation_policy ON inspections
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

CREATE POLICY tenant_isolation_policy ON inspection_items
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- Seed System Permissions for Operations Inspections
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'OPERATIONS_INSPECTION_READ', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_INSPECTION_WRITE', 'OPERATIONS'),
    (uuid_generate_v4(), 'OPERATIONS_INSPECTION_EXECUTE', 'OPERATIONS')
ON CONFLICT (code) DO NOTHING;
