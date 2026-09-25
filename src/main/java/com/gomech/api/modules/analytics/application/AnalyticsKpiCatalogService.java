package com.gomech.api.modules.analytics.application;

import com.gomech.api.modules.analytics.api.dto.AnalyticsDtos;
import com.gomech.api.modules.analytics.domain.KpiCategory;
import com.gomech.api.modules.analytics.domain.KpiDefinition;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AnalyticsKpiCatalogService {

    private static final List<KpiDefinition> KPI_DEFINITIONS = List.of(
            // OPERATIONS
            new KpiDefinition(
                    "WO_COMPLETED_COUNT",
                    "Ordens de Serviço Concluídas",
                    "Quantidade total de Ordens de Serviço finalizadas no período.",
                    KpiCategory.OPERATIONS,
                    "COUNT(work_orders WHERE status = 'COMPLETED')",
                    "unidades",
                    List.of("tenant_id", "unit_id", "date", "mechanic_user_id"),
                    "Real-time via WorkOrderCompletedEvent"
            ),
            new KpiDefinition(
                    "WO_TOTAL_REVENUE",
                    "Faturamento Total de OS",
                    "Receita bruta total gerada por ordens de serviço concluídas.",
                    KpiCategory.OPERATIONS,
                    "SUM(total_amount FROM completed work_orders)",
                    "R$",
                    List.of("tenant_id", "unit_id", "date", "mechanic_user_id"),
                    "Real-time via WorkOrderCompletedEvent"
            ),
            new KpiDefinition(
                    "WO_AVG_TICKET",
                    "Ticket Médio de OS",
                    "Valor médio faturado por ordem de serviço finalizada.",
                    KpiCategory.OPERATIONS,
                    "SUM(total_amount) / COUNT(completed work_orders)",
                    "R$",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time recalculation"
            ),
            new KpiDefinition(
                    "WO_AVG_TURNAROUND_HOURS",
                    "Tempo Médio de Atendimento (Lead Time)",
                    "Tempo médio decorrido entre a abertura e a conclusão das ordens de serviço.",
                    KpiCategory.OPERATIONS,
                    "AVG(completed_at - opened_at) em horas",
                    "horas",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time projection calculation"
            ),
            new KpiDefinition(
                    "QUOTE_CONVERSION_RATE",
                    "Taxa de Conversão de Orçamentos",
                    "Percentual de orçamentos aprovados pelo cliente em relação aos gerados.",
                    KpiCategory.OPERATIONS,
                    "(COUNT(approved quotes) / COUNT(total quotes)) * 100",
                    "%",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via QuoteCustomerDecisionEvent"
            ),
            new KpiDefinition(
                    "APPOINTMENT_COMPLETION_RATE",
                    "Taxa de Efetivação de Agendamentos",
                    "Percentual de agendamentos comparecidos / atendidos.",
                    KpiCategory.OPERATIONS,
                    "(COUNT(completed appointments) / COUNT(scheduled appointments)) * 100",
                    "%",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via AppointmentStatusChangedEvent"
            ),

            // FINANCE
            new KpiDefinition(
                    "FIN_GROSS_REVENUE",
                    "Receita Bruta Total",
                    "Total de recebíveis faturados e lançados no financeiro.",
                    KpiCategory.FINANCE,
                    "SUM(receivables amount)",
                    "R$",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via ReceivableCreatedEvent"
            ),
            new KpiDefinition(
                    "FIN_RECEIVABLES_PAID",
                    "Recebíveis Liquidados (Entradas)",
                    "Total de valores efetivamente recebidos no período.",
                    KpiCategory.FINANCE,
                    "SUM(receivables WHERE status = 'PAID')",
                    "R$",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via TransactionRecordedEvent"
            ),
            new KpiDefinition(
                    "FIN_PAYABLES_PAID",
                    "Contas Pagas (Saídas)",
                    "Total de despesas e compras liquidadas no período.",
                    KpiCategory.FINANCE,
                    "SUM(payables WHERE status = 'PAID')",
                    "R$",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via PayableCreatedEvent / TransactionRecordedEvent"
            ),
            new KpiDefinition(
                    "FIN_NET_PROFIT",
                    "Lucro Líquido Operacional",
                    "Diferença entre receitas realizadas e despesas pagas no período.",
                    KpiCategory.FINANCE,
                    "FIN_RECEIVABLES_PAID - FIN_PAYABLES_PAID",
                    "R$",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time calculation"
            ),
            new KpiDefinition(
                    "FIN_OPERATING_MARGIN",
                    "Margem Operacional",
                    "Percentual de margem líquida sobre a receita liquidada.",
                    KpiCategory.FINANCE,
                    "(FIN_NET_PROFIT / FIN_RECEIVABLES_PAID) * 100",
                    "%",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time calculation"
            ),
            new KpiDefinition(
                    "FIN_DELINQUENCY_RATE",
                    "Taxa de Inadimplência",
                    "Percentual de contas a receber vencidas e não pagas.",
                    KpiCategory.FINANCE,
                    "(SUM(overdue receivables) / SUM(total receivables)) * 100",
                    "%",
                    List.of("tenant_id", "unit_id", "date"),
                    "Projection rollup calculation"
            ),

            // INVENTORY
            new KpiDefinition(
                    "STOCK_PURCHASE_SPEND",
                    "Investimento em Compras de Peças",
                    "Valor total investido em aquisições e reposições de estoque.",
                    KpiCategory.INVENTORY,
                    "SUM(purchases total_amount)",
                    "R$",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via InventoryPurchaseCreatedEvent"
            ),
            new KpiDefinition(
                    "STOCK_CONSUMED_VALUE",
                    "Consumo de Peças em Serviços",
                    "Custo total de peças e insumos aplicados em ordens de serviço.",
                    KpiCategory.INVENTORY,
                    "SUM(consumed_quantity * unit_cost)",
                    "R$",
                    List.of("tenant_id", "unit_id", "date", "product_id"),
                    "Real-time via StockConsumedEvent"
            ),

            // TOOLS
            new KpiDefinition(
                    "TOOL_ACTIVE_CUSTODY",
                    "Equipamentos em Custódia",
                    "Total de ferramentas e equipamentos sob uso de mecânicos.",
                    KpiCategory.TOOLS,
                    "COUNT(tools WHERE status = 'IN_USE')",
                    "unidades",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via ToolCustodyAssignedEvent"
            ),
            new KpiDefinition(
                    "TOOL_MAINTENANCE_COST",
                    "Custo de Manutenção de Ferramentas",
                    "Gasto com reparos, calibrações e manutenção de equipamentos.",
                    KpiCategory.TOOLS,
                    "SUM(maintenance cost)",
                    "R$",
                    List.of("tenant_id", "unit_id", "date"),
                    "Real-time via ToolMaintenanceCompletedEvent"
            ),

            // BILLING
            new KpiDefinition(
                    "BILLING_REPORTS_QUOTA",
                    "Consumo de Relatórios & Analytics",
                    "Volume de relatórios e exportações executados no ciclo atual.",
                    KpiCategory.BILLING,
                    "COUNT(report exports generated)",
                    "execuções",
                    List.of("tenant_id", "billing_cycle"),
                    "On-demand via Entitlement Quota"
            )
    );

    public List<KpiDefinition> getAllKpiDefinitions() {
        return KPI_DEFINITIONS;
    }

    public List<AnalyticsDtos.KpiDefinitionDto> getCatalogDtos() {
        return KPI_DEFINITIONS.stream()
                .map(k -> AnalyticsDtos.KpiDefinitionDto.builder()
                        .code(k.code())
                        .name(k.name())
                        .description(k.description())
                        .category(k.category())
                        .formula(k.formula())
                        .unit(k.unit())
                        .dimensions(k.dimensions())
                        .refreshPolicy(k.refreshPolicy())
                        .build())
                .toList();
    }
}
