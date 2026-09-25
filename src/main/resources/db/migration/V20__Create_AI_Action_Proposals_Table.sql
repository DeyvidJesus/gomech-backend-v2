-- V20__Create_AI_Action_Proposals_Table.sql
-- Creates table for AI Action Proposals requiring mandatory human-in-the-loop confirmation before command execution.

CREATE TABLE IF NOT EXISTS ai_action_proposals (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    unit_id UUID REFERENCES units(id) ON DELETE SET NULL,
    actor_user_id UUID NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    target_resource_type VARCHAR(64),
    target_resource_id UUID,
    title VARCHAR(255) NOT NULL,
    summary TEXT,
    payload_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    rejection_reason VARCHAR(500),
    expires_at TIMESTAMPTZ NOT NULL,
    confirmed_by_user_id UUID,
    confirmed_at TIMESTAMPTZ,
    executed_at TIMESTAMPTZ,
    execution_result_json TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes for efficient queries
CREATE INDEX IF NOT EXISTS idx_ai_action_proposals_tenant_status ON ai_action_proposals(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_ai_action_proposals_expires_at ON ai_action_proposals(expires_at);
CREATE INDEX IF NOT EXISTS idx_ai_action_proposals_target ON ai_action_proposals(tenant_id, target_resource_type, target_resource_id);

-- Enable Row Level Security (RLS)
ALTER TABLE ai_action_proposals ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_policy ON ai_action_proposals;
CREATE POLICY tenant_isolation_policy ON ai_action_proposals
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant', true), '')::uuid);

-- Seed System Permissions
INSERT INTO permissions (id, name, description, category, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'AI_ACTION_PROPOSE', 'Permite propor ações estruturadas através de IA', 'AI', NOW(), NOW()),
    (gen_random_uuid(), 'AI_ACTION_CONFIRM', 'Permite revisar, confirmar e executar propostas de ações de IA', 'AI', NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- Grant permissions to default roles
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE p.name IN ('AI_ACTION_PROPOSE', 'AI_ACTION_CONFIRM')
  AND r.name IN ('Proprietário', 'ADMIN', 'Mecânico', 'Atendente')
ON CONFLICT DO NOTHING;
