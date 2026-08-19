-- ============================================================================
-- V7: Create Billing, Plans, Plan Features, Usage Records and Seed Default Plans
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. Billing Plans (Catalog of subscription tiers)
-- ----------------------------------------------------------------------------
CREATE TABLE billing_plans (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    price DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    billing_interval VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ----------------------------------------------------------------------------
-- 2. Billing Plan Features & Quotas
-- ----------------------------------------------------------------------------
CREATE TABLE billing_plan_features (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    plan_id UUID NOT NULL REFERENCES billing_plans(id) ON DELETE CASCADE,
    feature_code VARCHAR(100) NOT NULL,
    limit_value BIGINT NOT NULL DEFAULT -1, -- -1 = Unlimited
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    unit_of_measure VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_plan_feature UNIQUE (plan_id, feature_code)
);

CREATE INDEX idx_plan_features_plan_id ON billing_plan_features(plan_id);

-- ----------------------------------------------------------------------------
-- 3. Enhance Subscriptions table with plan relations and billing period
-- ----------------------------------------------------------------------------
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_id UUID REFERENCES billing_plans(id);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_code VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS current_period_start TIMESTAMP WITH TIME ZONE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS current_period_end TIMESTAMP WITH TIME ZONE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS trial_ends_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS cancel_at_period_end BOOLEAN DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_subscriptions_tenant_status ON subscriptions(tenant_id, status);

-- ----------------------------------------------------------------------------
-- 4. Usage Records (Quota tracking per dimension and period)
-- ----------------------------------------------------------------------------
CREATE TABLE usage_records (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    unit_id UUID REFERENCES units(id),
    dimension VARCHAR(50) NOT NULL,
    amount BIGINT NOT NULL DEFAULT 0,
    period_start TIMESTAMP WITH TIME ZONE NOT NULL,
    period_end TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_usage_tenant_dim_period UNIQUE (tenant_id, dimension, period_start, period_end)
);

CREATE INDEX idx_usage_records_tenant ON usage_records(tenant_id, dimension);

-- Enable RLS on usage_records
ALTER TABLE usage_records ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON usage_records
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- ----------------------------------------------------------------------------
-- 5. Seed Standard Plans and Entitlements
-- ----------------------------------------------------------------------------

-- A) TRIAL Plan (Gratuito - Avaliação 14 dias)
INSERT INTO billing_plans (id, code, name, description, price, billing_interval, is_active)
VALUES ('11111111-1111-1111-1111-111111111111', 'TRIAL', 'Trial Gratuito', 'Plano de teste com acesso completo e limites controlados para avaliação inicial', 0.00, 'MONTHLY', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure) VALUES
('11111111-1111-1111-1111-111111111111', 'USERS', 2, TRUE, 'COUNT'),
('11111111-1111-1111-1111-111111111111', 'UNITS', 1, TRUE, 'COUNT'),
('11111111-1111-1111-1111-111111111111', 'AI_USAGE', 100, TRUE, 'REQUESTS'),
('11111111-1111-1111-1111-111111111111', 'STORAGE_MB', 500, TRUE, 'MB'),
('11111111-1111-1111-1111-111111111111', 'WHATSAPP_MESSAGES', 50, TRUE, 'MESSAGES'),
('11111111-1111-1111-1111-111111111111', 'REPORTS', 10, TRUE, 'COUNT'),
('11111111-1111-1111-1111-111111111111', 'MODULE_CRM', -1, TRUE, 'BOOLEAN'),
('11111111-1111-1111-1111-111111111111', 'MODULE_OPERATIONS', -1, TRUE, 'BOOLEAN'),
('11111111-1111-1111-1111-111111111111', 'MODULE_INVENTORY', -1, TRUE, 'BOOLEAN'),
('11111111-1111-1111-1111-111111111111', 'MODULE_FINANCE', 0, FALSE, 'BOOLEAN'),
('11111111-1111-1111-1111-111111111111', 'MODULE_AI', -1, TRUE, 'BOOLEAN')
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- B) STARTER Plan
INSERT INTO billing_plans (id, code, name, description, price, billing_interval, is_active)
VALUES ('22222222-2222-2222-2222-222222222222', 'STARTER', 'Starter', 'Ideal para oficinas independentes e mecânicos autônomos', 149.90, 'MONTHLY', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure) VALUES
('22222222-2222-2222-2222-222222222222', 'USERS', 3, TRUE, 'COUNT'),
('22222222-2222-2222-2222-222222222222', 'UNITS', 1, TRUE, 'COUNT'),
('22222222-2222-2222-2222-222222222222', 'AI_USAGE', 500, TRUE, 'REQUESTS'),
('22222222-2222-2222-2222-222222222222', 'STORAGE_MB', 2000, TRUE, 'MB'),
('22222222-2222-2222-2222-222222222222', 'WHATSAPP_MESSAGES', 200, TRUE, 'MESSAGES'),
('22222222-2222-2222-2222-222222222222', 'REPORTS', 50, TRUE, 'COUNT'),
('22222222-2222-2222-2222-222222222222', 'MODULE_CRM', -1, TRUE, 'BOOLEAN'),
('22222222-2222-2222-2222-222222222222', 'MODULE_OPERATIONS', -1, TRUE, 'BOOLEAN'),
('22222222-2222-2222-2222-222222222222', 'MODULE_INVENTORY', -1, TRUE, 'BOOLEAN'),
('22222222-2222-2222-2222-222222222222', 'MODULE_FINANCE', 0, FALSE, 'BOOLEAN'),
('22222222-2222-2222-2222-222222222222', 'MODULE_AI', -1, TRUE, 'BOOLEAN')
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- C) PRO Plan
INSERT INTO billing_plans (id, code, name, description, price, billing_interval, is_active)
VALUES ('33333333-3333-3333-3333-333333333333', 'PRO', 'Profissional', 'Para oficinas consolidadas com múltiplos boxes e controle financeiro completo', 299.90, 'MONTHLY', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure) VALUES
('33333333-3333-3333-3333-333333333333', 'USERS', 10, TRUE, 'COUNT'),
('33333333-3333-3333-3333-333333333333', 'UNITS', 3, TRUE, 'COUNT'),
('33333333-3333-3333-3333-333333333333', 'AI_USAGE', 5000, TRUE, 'REQUESTS'),
('33333333-3333-3333-3333-333333333333', 'STORAGE_MB', 10000, TRUE, 'MB'),
('33333333-3333-3333-3333-333333333333', 'WHATSAPP_MESSAGES', 1000, TRUE, 'MESSAGES'),
('33333333-3333-3333-3333-333333333333', 'REPORTS', 500, TRUE, 'COUNT'),
('33333333-3333-3333-3333-333333333333', 'MODULE_CRM', -1, TRUE, 'BOOLEAN'),
('33333333-3333-3333-3333-333333333333', 'MODULE_OPERATIONS', -1, TRUE, 'BOOLEAN'),
('33333333-3333-3333-3333-333333333333', 'MODULE_INVENTORY', -1, TRUE, 'BOOLEAN'),
('33333333-3333-3333-3333-333333333333', 'MODULE_FINANCE', -1, TRUE, 'BOOLEAN'),
('33333333-3333-3333-3333-333333333333', 'MODULE_AI', -1, TRUE, 'BOOLEAN')
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- D) ENTERPRISE Plan
INSERT INTO billing_plans (id, code, name, description, price, billing_interval, is_active)
VALUES ('44444444-4444-4444-4444-444444444444', 'ENTERPRISE', 'Enterprise / Redes', 'Ilimitado para redes de oficinas, centros automotivos e frotistas', 699.90, 'MONTHLY', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure) VALUES
('44444444-4444-4444-4444-444444444444', 'USERS', -1, TRUE, 'COUNT'),
('44444444-4444-4444-4444-444444444444', 'UNITS', -1, TRUE, 'COUNT'),
('44444444-4444-4444-4444-444444444444', 'AI_USAGE', 50000, TRUE, 'REQUESTS'),
('44444444-4444-4444-4444-444444444444', 'STORAGE_MB', 50000, TRUE, 'MB'),
('44444444-4444-4444-4444-444444444444', 'WHATSAPP_MESSAGES', 10000, TRUE, 'MESSAGES'),
('44444444-4444-4444-4444-444444444444', 'REPORTS', -1, TRUE, 'COUNT'),
('44444444-4444-4444-4444-444444444444', 'MODULE_CRM', -1, TRUE, 'BOOLEAN'),
('44444444-4444-4444-4444-444444444444', 'MODULE_OPERATIONS', -1, TRUE, 'BOOLEAN'),
('44444444-4444-4444-4444-444444444444', 'MODULE_INVENTORY', -1, TRUE, 'BOOLEAN'),
('44444444-4444-4444-4444-444444444444', 'MODULE_FINANCE', -1, TRUE, 'BOOLEAN'),
('44444444-4444-4444-4444-444444444444', 'MODULE_AI', -1, TRUE, 'BOOLEAN')
ON CONFLICT (plan_id, feature_code) DO NOTHING;
