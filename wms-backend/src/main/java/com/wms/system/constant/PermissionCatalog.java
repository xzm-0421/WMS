package com.wms.system.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public enum PermissionCatalog {

    DASHBOARD_VIEW("dashboard:view", "工作台", "dashboard"),

    SYSTEM_USER_LIST("system:user:list", "用户查询", "system"),
    SYSTEM_USER_ADD("system:user:add", "用户新增", "system"),
    SYSTEM_USER_EDIT("system:user:edit", "用户编辑", "system"),
    SYSTEM_USER_DELETE("system:user:delete", "用户删除", "system"),
    SYSTEM_ROLE_LIST("system:role:list", "角色查询", "system"),
    SYSTEM_ROLE_ADD("system:role:add", "角色新增", "system"),
    SYSTEM_ROLE_EDIT("system:role:edit", "角色编辑", "system"),
    SYSTEM_ROLE_DELETE("system:role:delete", "角色删除", "system"),
    SYSTEM_ROLE_PERM("system:role:perm", "角色权限配置", "system"),

    BASE_MATERIAL_LIST("base:material:list", "物料查询", "base"),
    BASE_WAREHOUSE_LIST("base:warehouse:list", "仓库查询", "base"),
    BASE_SUPPLIER_LIST("base:supplier:list", "供应商查询", "base"),

    INBOUND_LIST("inbound:list", "入库单查询", "inbound"),
    INBOUND_ADD("inbound:add", "入库单新增", "inbound"),
    INBOUND_EDIT("inbound:edit", "入库单编辑", "inbound"),
    INBOUND_AUDIT("inbound:audit", "入库单审核", "inbound"),
    INBOUND_RECORD_LIST("inbound:record:list", "PDA入库记录查询", "inbound"),
    INBOUND_RECORD_AUDIT("inbound:record:audit", "PDA入库记录审核", "inbound"),
    INBOUND_RECORD_REVERSE("inbound:record:reverse", "PDA入库记录冲销", "inbound"),
    INBOUND_RECORD_RESYNC("inbound:record:resync", "PDA入库记录重同步", "inbound"),

    OUTBOUND_LIST("outbound:list", "出库单查询", "outbound"),
    OUTBOUND_ADD("outbound:add", "出库单新增", "outbound"),
    OUTBOUND_EDIT("outbound:edit", "出库单编辑", "outbound"),
    OUTBOUND_AUDIT("outbound:audit", "出库单审核", "outbound"),
    OUTBOUND_RECORD_LIST("outbound:record:list", "PDA出库记录查询", "outbound"),

    INVENTORY_LIST("inventory:list", "库存查询", "inventory"),

    STOCKTAKE_LIST("stocktake:list", "盘点查询", "stocktake"),
    STOCKTAKE_ADD("stocktake:add", "盘点新增", "stocktake"),

    QC_LIST("qc:list", "质检查询", "qc"),
    QC_EDIT("qc:edit", "质检操作", "qc"),

    PRODUCTION_LIST("production:list", "生产订单查询", "production"),

    BARCODE_LIST("barcode:list", "条码规则查询", "barcode"),

    PRINT_LABEL_LIST("print:label:list", "物料标签打印", "print"),
    PRINT_LABEL_CREATE("print:label:create", "物料标签打印创建", "print"),

    REPORT_VIEW("report:view", "报表查看", "report"),

    BASE_LOCATION_LIST("base:location:list", "库位查询", "base"),
    INCOMING_PO_LIST("incoming:po:list", "采购订单查询", "incoming"),
    INCOMING_DN_LIST("incoming:dn:list", "送货单查询", "incoming"),
    INCOMING_RETURN_LIST("incoming:return:list", "采购退货查询", "incoming"),
    PICKING_PREP_LIST("picking:prep:list", "备料通知查询", "picking"),
    PICKING_ISSUE_LIST("picking:issue:list", "拣配发料查询", "picking"),
    PICKING_PICKUP_LIST("picking:pickup:list", "领料执行查询", "picking"),
    PICKING_WORKSHOP_RETURN("picking:workshop-return:list", "车间退库查询", "picking"),
    INVENTORY_OTHER_IN("inventory:other-in:list", "其他入库查询", "inventory"),
    INVENTORY_OTHER_OUT("inventory:other-out:list", "其他出库查询", "inventory"),
    INVENTORY_TRANSFER_LIST("inventory:transfer:list", "库存调拨查询", "inventory"),
    INVENTORY_SAMPLE_LIST("inventory:sample:list", "库存抽检查询", "inventory"),
    DASHBOARD_WAREHOUSE("dashboard:warehouse", "仓库看板", "dashboard"),
    DASHBOARD_PICKING("dashboard:picking", "拣配看板", "dashboard"),
    SYSTEM_RULE_LIST("system:rule:list", "业务规则配置", "system"),
    SYSTEM_PRINT_DESIGN("system:print:design", "套打设计器", "system"),
    SYSTEM_FLOW_VIEW("system:flow:view", "业务流程手册", "system"),

    MES_MASTER_LIST("mes:master:list", "MES基础资料查询", "mes"),
    MES_MASTER_SYNC("mes:master:sync", "MES基础资料同步", "mes"),
    MES_MATERIAL_LIST("mes:material:list", "物料查询", "mes"),
    MES_MATERIAL_SYNC("mes:material:sync", "物料同步", "mes"),
    MES_BOM_LIST("mes:bom:list", "BOM查询", "mes"),
    MES_BOM_SYNC("mes:bom:sync", "BOM同步", "mes"),
    MES_PROCESS_LIST("mes:process:list", "工序查询", "mes"),
    MES_PROCESS_SYNC("mes:process:sync", "工序同步", "mes"),
    MES_EQUIPMENT_LIST("mes:equipment:list", "设备查询", "mes"),
    MES_EQUIPMENT_SYNC("mes:equipment:sync", "设备同步", "mes"),
    MES_ROUTE_LIST("mes:route:list", "工艺路线查询", "mes"),
    MES_ROUTE_SYNC("mes:route:sync", "工艺路线同步", "mes"),
    MES_PLAN_LIST("mes:plan:list", "工序计划查询", "mes"),
    MES_PLAN_RETRY("mes:plan:retry", "工序计划重试同步", "mes"),
    MES_REPORT_SUBMIT("mes:report:submit", "工序报工", "mes"),
    MES_REPORT_LIST("mes:report:list", "报工记录查询", "mes"),
    MES_REPORT_LIST_ALL("mes:report:list:all", "报工记录查看全部", "mes"),
    MES_REPORT_RETRY("mes:report:retry", "报工同步重试", "mes"),
    MES_REPORT_CANCEL("mes:report:cancel", "取消报工暂存", "mes"),
    MES_REPORT_EXPORT("mes:report:export", "导出报工记录", "mes"),
    MES_TRANSFER_SUBMIT("mes:transfer:submit", "工序转移", "mes"),
    MES_REWORK_CREATE("mes:rework:create", "发起返工", "mes"),
    MES_REWORK_REPORT("mes:rework:report", "返工报工", "mes"),
    MES_REWORK_VIEW("mes:rework:view", "查看返工序列", "mes"),
    MES_SYNC_PANEL("mes:sync:panel", "MES同步中心", "mes");

    private final String code;
    private final String name;
    private final String module;

    public static List<PermissionCatalog> all() {
        return Arrays.asList(values());
    }
}
