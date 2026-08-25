-- ==============================================================================
-- V17: Add Pagar.me Plan IDs, Customer IDs, and Enhance Hosted Checkout Schema
-- ==============================================================================

-- 1. Add pagarme_plan_id to billing_plans
ALTER TABLE billing_plans ADD COLUMN IF NOT EXISTS pagarme_plan_id VARCHAR(100);

-- Update existing default plans with official Pagar.me V5 Plan IDs
UPDATE billing_plans SET pagarme_plan_id = 'plan_QjP9YkMU7KHxomNz' WHERE code = 'STARTER' AND (pagarme_plan_id IS NULL OR pagarme_plan_id = '');
UPDATE billing_plans SET pagarme_plan_id = 'plan_veoYEdYhdxU9qJ9X' WHERE code = 'PRO' AND (pagarme_plan_id IS NULL OR pagarme_plan_id = '');
UPDATE billing_plans SET pagarme_plan_id = 'plan_2jVwBLXIVEiKR3xq' WHERE code = 'ENTERPRISE' AND (pagarme_plan_id IS NULL OR pagarme_plan_id = '');

-- 2. Add gateway_customer_id to tenants
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS gateway_customer_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_tenants_gateway_customer ON tenants(gateway_customer_id);

-- 3. Ensure subscriptions has gateway identifiers and indexes
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS gateway_customer_id VARCHAR(100);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS gateway_subscription_id VARCHAR(100);
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS gateway_payment_link_id VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_subscriptions_gw_sub ON subscriptions(gateway_subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_gw_cust ON subscriptions(gateway_customer_id);

-- 4. Ensure payments has gateway identifiers and indexes
ALTER TABLE payments ADD COLUMN IF NOT EXISTS gateway_payment_link_id VARCHAR(100);
CREATE INDEX IF NOT EXISTS idx_payments_gw_link ON payments(gateway_payment_link_id);
