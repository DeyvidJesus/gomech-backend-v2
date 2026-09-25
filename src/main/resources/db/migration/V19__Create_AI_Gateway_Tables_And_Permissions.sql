-- ==============================================================================
-- V19: Create AI Gateway Audit Log Tables, Permissions and RLS Policies
-- ==============================================================================

-- 1. Tabela de Logs de Auditoria do Gateway de IA
CREATE TABLE IF NOT EXISTS ai_gateway_audit_logs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    unit_id UUID,
    user_id UUID,
    correlation_id VARCHAR(100),
    capability VARCHAR(50) NOT NULL, -- DIAGNOSTIC_ASSIST, QUOTE_GENERATION, WORK_ORDER_SUMMARY, CUSTOMER_MESSAGE_DRAFT, GENERAL_COMPLETION
    model VARCHAR(100) NOT NULL,
    prompt_tokens INT NOT NULL DEFAULT 0,
    completion_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    latency_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL, -- SUCCESS, FAILED, REJECTED_UNAUTHORIZED, REJECTED_QUOTA_EXCEEDED, RATE_LIMITED, TIMEOUT
    error_code VARCHAR(100),
    redacted_prompt_summary VARCHAR(1000),
    redacted_response_summary VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ai_audit_tenant_created ON ai_gateway_audit_logs(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_audit_capability ON ai_gateway_audit_logs(tenant_id, capability);
CREATE INDEX IF NOT EXISTS idx_ai_audit_status ON ai_gateway_audit_logs(tenant_id, status);

-- 2. Habilitação de Row Level Security (RLS)
ALTER TABLE ai_gateway_audit_logs ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE tablename = 'ai_gateway_audit_logs' 
        AND policyname = 'tenant_isolation_policy'
    ) THEN
        CREATE POLICY tenant_isolation_policy ON ai_gateway_audit_logs
            USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), '')::uuid);
    END IF;
END $$;

-- 3. Semeadura de Permissões de Sistema para o Módulo de IA
INSERT INTO permissions (id, code, module) VALUES
    (uuid_generate_v4(), 'AI_QUERY', 'AI'),
    (uuid_generate_v4(), 'AI_ACTION_EXECUTE', 'AI'),
    (uuid_generate_v4(), 'AI_ADMIN', 'AI')
ON CONFLICT (code) DO NOTHING;

-- 4. Associação de Permissões aos Papéis Padrão
DO $$
DECLARE
    role_owner_id UUID;
    role_admin_id UUID;
    role_mechanic_id UUID;
    role_attendant_id UUID;
    perm_query_id UUID;
    perm_action_id UUID;
    perm_admin_id UUID;
    r RECORD;
BEGIN
    SELECT id INTO perm_query_id FROM permissions WHERE code = 'AI_QUERY';
    SELECT id INTO perm_action_id FROM permissions WHERE code = 'AI_ACTION_EXECUTE';
    SELECT id INTO perm_admin_id FROM permissions WHERE code = 'AI_ADMIN';

    -- Atribuir permissões para todos os tenants existentes
    FOR r IN SELECT id FROM tenants LOOP
        -- Proprietário
        SELECT id INTO role_owner_id FROM roles WHERE tenant_id = r.id AND name = 'Proprietário';
        IF role_owner_id IS NOT NULL THEN
            IF perm_query_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_owner_id, perm_query_id) ON CONFLICT DO NOTHING;
            END IF;
            IF perm_action_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_owner_id, perm_action_id) ON CONFLICT DO NOTHING;
            END IF;
            IF perm_admin_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_owner_id, perm_admin_id) ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        -- ADMIN
        SELECT id INTO role_admin_id FROM roles WHERE tenant_id = r.id AND name = 'ADMIN';
        IF role_admin_id IS NOT NULL THEN
            IF perm_query_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_admin_id, perm_query_id) ON CONFLICT DO NOTHING;
            END IF;
            IF perm_action_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_admin_id, perm_action_id) ON CONFLICT DO NOTHING;
            END IF;
            IF perm_admin_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_admin_id, perm_admin_id) ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        -- Mecânico (Consulta diagnósticos e executa checklists)
        SELECT id INTO role_mechanic_id FROM roles WHERE tenant_id = r.id AND name = 'Mecânico';
        IF role_mechanic_id IS NOT NULL THEN
            IF perm_query_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_mechanic_id, perm_query_id) ON CONFLICT DO NOTHING;
            END IF;
            IF perm_action_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_mechanic_id, perm_action_id) ON CONFLICT DO NOTHING;
            END IF;
        END IF;

        -- Atendente (Consulta assistente e gera rascunhos de mensagens/orçamentos)
        SELECT id INTO role_attendant_id FROM roles WHERE tenant_id = r.id AND name = 'Atendente';
        IF role_attendant_id IS NOT NULL THEN
            IF perm_query_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_attendant_id, perm_query_id) ON CONFLICT DO NOTHING;
            END IF;
            IF perm_action_id IS NOT NULL THEN
                INSERT INTO role_permissions (role_id, permission_id) VALUES (role_attendant_id, perm_action_id) ON CONFLICT DO NOTHING;
            END IF;
        END IF;
    END LOOP;
END $$;
