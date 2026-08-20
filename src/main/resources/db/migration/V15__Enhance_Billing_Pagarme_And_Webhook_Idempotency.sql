-- ==============================================================================
-- V15: Enhance Billing with Pagar.me Gateway Fields, Webhook Idempotency, and Module Entitlements
-- ==============================================================================

-- 1. Enhance Subscriptions with gateway & payment details
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS gateway_customer_id VARCHAR(100);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS payment_method VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS card_last_four VARCHAR(4);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS card_brand VARCHAR(50);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS delinquent_since TIMESTAMP WITH TIME ZONE;
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS canceled_at TIMESTAMP WITH TIME ZONE;

-- 2. Enhance Payments table with gateway metadata, PIX and Boleto fields
ALTER TABLE payments ADD COLUMN IF NOT EXISTS tenant_id UUID REFERENCES tenants(id);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_order_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_charge_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_payment_id VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pix_qr_code TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pix_qr_code_url TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pix_copy_paste TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS pix_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS boleto_barcode VARCHAR(100);
ALTER TABLE payments ADD COLUMN IF NOT EXISTS boleto_url TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS boleto_due_date DATE;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS installments INT DEFAULT 1;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS refunded_amount DECIMAL(10, 2) DEFAULT 0.00;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_response_raw TEXT;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_payments_tenant ON payments(tenant_id);
CREATE INDEX IF NOT EXISTS idx_payments_gateway_order ON payments(gateway_order_id);
CREATE INDEX IF NOT EXISTS idx_payments_gateway_charge ON payments(gateway_charge_id);

-- Enable RLS on payments
ALTER TABLE payments ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON payments
    FOR ALL
    USING (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id IS NULL OR tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- 3. Processed Webhook Events (Idempotency Key Persistence)
CREATE TABLE IF NOT EXISTS processed_webhook_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload_hash VARCHAR(64),
    source VARCHAR(50) NOT NULL DEFAULT 'PAGARME',
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSED',
    error_details TEXT
);

CREATE INDEX IF NOT EXISTS idx_processed_webhooks_event_id ON processed_webhook_events(event_id);

-- 4. Update Billing Plan Features with new modules (MODULE_TOOLS, MODULE_FINANCE)
-- TRIAL Plan
INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure) VALUES
('11111111-1111-1111-1111-111111111111', 'MODULE_TOOLS', -1, TRUE, 'BOOLEAN'),
('11111111-1111-1111-1111-111111111111', 'MODULE_FINANCE', -1, TRUE, 'BOOLEAN')
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- STARTER Plan (Starter has basic modules, no advanced finance)
INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure)
SELECT id, 'MODULE_TOOLS', -1, TRUE, 'BOOLEAN' FROM billing_plans WHERE code = 'STARTER'
ON CONFLICT (plan_id, feature_code) DO NOTHING;

INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure)
SELECT id, 'MODULE_FINANCE', 0, FALSE, 'BOOLEAN' FROM billing_plans WHERE code = 'STARTER'
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- PRO Plan (Full modules)
INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure)
SELECT id, 'MODULE_TOOLS', -1, TRUE, 'BOOLEAN' FROM billing_plans WHERE code = 'PRO'
ON CONFLICT (plan_id, feature_code) DO NOTHING;

INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure)
SELECT id, 'MODULE_FINANCE', -1, TRUE, 'BOOLEAN' FROM billing_plans WHERE code = 'PRO'
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- ENTERPRISE Plan (Full modules unlimited)
INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure)
SELECT id, 'MODULE_TOOLS', -1, TRUE, 'BOOLEAN' FROM billing_plans WHERE code = 'ENTERPRISE'
ON CONFLICT (plan_id, feature_code) DO NOTHING;

INSERT INTO billing_plan_features (plan_id, feature_code, limit_value, enabled, unit_of_measure)
SELECT id, 'MODULE_FINANCE', -1, TRUE, 'BOOLEAN' FROM billing_plans WHERE code = 'ENTERPRISE'
ON CONFLICT (plan_id, feature_code) DO NOTHING;

-- 5. Seed Billing Permissions
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'BILLING_READ', 'BILLING'),
    (uuid_generate_v4(), 'BILLING_WRITE', 'BILLING'),
    (uuid_generate_v4(), 'BILLING_ADMIN', 'BILLING')
ON CONFLICT (code) DO NOTHING;

-- Vincular novas permissões de billing aos papéis de Proprietário e Administrador
DO $$
DECLARE
    prop_role_id UUID;
    admin_role_id UUID;
    perm_rec RECORD;
BEGIN
    FOR prop_role_id IN SELECT id FROM roles WHERE name = 'Proprietário' LOOP
        FOR perm_rec IN SELECT id FROM permissions WHERE module = 'BILLING' LOOP
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (prop_role_id, perm_rec.id)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;

    FOR admin_role_id IN SELECT id FROM roles WHERE name = 'ADMIN' LOOP
        FOR perm_rec IN SELECT id FROM permissions WHERE module = 'BILLING' LOOP
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (admin_role_id, perm_rec.id)
            ON CONFLICT DO NOTHING;
        END LOOP;
    END LOOP;
END $$;
