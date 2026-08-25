-- ==============================================================================
-- V16: Enhance Tenants, Units, Appointments, Inspections & Quotes with Profiles & Team Workflows
-- ==============================================================================

-- Enhance tenants table
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS trade_name VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS logo_url TEXT;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS address TEXT;

-- Enhance units table
ALTER TABLE units ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
ALTER TABLE units ADD COLUMN IF NOT EXISTS logo_url TEXT;
ALTER TABLE units ADD COLUMN IF NOT EXISTS technical_manager VARCHAR(255);

-- Enhance appointments with assigned user for team dispatch
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS assigned_user_id UUID REFERENCES users(id);

-- Enhance inspections with inspector user
ALTER TABLE inspections ADD COLUMN IF NOT EXISTS inspector_user_id UUID REFERENCES users(id);

-- Enhance quotes with reviewer user
ALTER TABLE quotes ADD COLUMN IF NOT EXISTS reviewer_user_id UUID REFERENCES users(id);

-- Seed System Permissions for Company Settings
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'IAM_COMPANY_READ', 'IAM'),
    (uuid_generate_v4(), 'IAM_COMPANY_WRITE', 'IAM')
ON CONFLICT (code) DO NOTHING;
