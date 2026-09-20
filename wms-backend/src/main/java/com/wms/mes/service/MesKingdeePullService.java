package com.wms.mes.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.common.exception.BusinessException;
import com.wms.common.constant.ErrorCode;
import com.wms.integration.kingdee.KingdeeBomService;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeMasterDataSyncService;
import com.wms.integration.kingdee.dto.KingdeeMasterDataSyncResult;
import com.wms.mes.MesConstants;
import com.wms.mes.domain.MesPlanChangePolicy;
import com.wms.mes.domain.MesPlanKey;
import com.wms.mes.dto.MesSyncResult;
import com.wms.mes.entity.MesEquipment;
import com.wms.mes.entity.MesOpPlan;
import com.wms.mes.entity.MesProcess;
import com.wms.mes.entity.MesRoute;
import com.wms.mes.entity.MesRouteOp;
import com.wms.mes.kingdee.MesKingdeeFormIds;
import com.wms.mes.kingdee.MesKingdeeRow;
import com.wms.mes.mapper.MesEquipmentMapper;
import com.wms.mes.mapper.MesOpPlanMapper;
import com.wms.mes.mapper.MesProcessMapper;
import com.wms.mes.mapper.MesRouteMapper;
import com.wms.mes.mapper.MesRouteOpMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 按金蝶 OpenAPI executeBillQuery 拉取物料、BOM、工艺路线、工序计划、设备等到本地。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesKingdeePullService {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties kingdeeProperties;
    private final KingdeeMasterDataSyncService masterDataSyncService;
    private final KingdeeBomService kingdeeBomService;
    private final MesProcessMapper processMapper;
    private final MesEquipmentMapper equipmentMapper;
    private final MesRouteMapper routeMapper;
    private final MesRouteOpMapper routeOpMapper;
    private final MesOpPlanMapper opPlanMapper;

    public List<MesSyncResult> syncMasterData() {
        List<MesSyncResult> results = new ArrayList<>();
        results.add(syncMaterials());
        results.add(syncBoms());
        results.add(syncProcesses());
        results.add(syncEquipment());
        results.add(syncRoutes());
        return results;
    }

    public MesSyncResult syncMaterials() {
        MesSyncResult result = new MesSyncResult();
        result.setType("物料");
        if (!kingdeeCloudService.isEnabled()) {
            result.setSuccess(false);
            result.setMessage("金蝶对接未启用，请在配置中开启 kingdee.cloud.enabled");
            return result;
        }
        try {
            KingdeeMasterDataSyncResult sync = masterDataSyncService.syncMaterials(null);
            result.setFetched(sync.getTotalFetched());
            result.setInserted(sync.getInserted());
            result.setUpdated(sync.getUpdated());
            result.setSkipped(sync.getSkipped());
            result.setSuccess(true);
            result.setMessage(sync.getMessage());
            return result;
        } catch (Exception e) {
            log.error("MES material sync failed", e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            return result;
        }
    }

    public MesSyncResult syncBoms() {
        MesSyncResult result = new MesSyncResult();
        result.setType("BOM");
        if (!kingdeeCloudService.isEnabled()) {
            result.setSuccess(false);
            result.setMessage("金蝶对接未启用，请在配置中开启 kingdee.cloud.enabled");
            return result;
        }
        try {
            KingdeeMasterDataSyncResult sync = kingdeeBomService.syncAllToLocal();
            result.setFetched(sync.getTotalFetched());
            result.setInserted(sync.getInserted());
            result.setUpdated(sync.getUpdated());
            result.setSkipped(sync.getSkipped());
            result.setSuccess(true);
            result.setMessage(sync.getMessage());
            return result;
        } catch (Exception e) {
            log.error("MES BOM sync failed", e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            return result;
        }
    }

    public MesSyncResult syncProcesses() {
        return pull("工序", kingdeeProperties.getMesProcessFormId(), kingdeeProperties.getMesProcessFieldKeys(),
                this::upsertProcess);
    }

    public MesSyncResult syncEquipment() {
        return pull("设备", kingdeeProperties.getMesEquipmentFormId(), kingdeeProperties.getMesEquipmentFieldKeys(),
                this::upsertEquipment);
    }

    public MesSyncResult syncRoutes() {
        MesSyncResult result = new MesSyncResult();
        result.setType("工艺路线");
        if (!kingdeeCloudService.isEnabled()) {
            result.setSuccess(false);
            result.setMessage("金蝶对接未启用，请在配置中开启 kingdee.cloud.enabled");
            return result;
        }
        try {
            Map<String, List<MesKingdeeRow>> grouped = new LinkedHashMap<>();
            int fetched = queryAll(kingdeeProperties.getMesRouteFormId(), kingdeeProperties.getMesRouteFieldKeys(),
                    "ENG_Route", kingdeeProperties.getMesRouteAltFieldKeys(),
                    masterFilter(), row -> {
                        String routeCode = row.get("FNumber");
                        String product = row.get("FMaterialID.FNumber", "FMaterialId.FNumber", "FProductId.FNumber");
                        String version = firstNonBlank(row.get("FVersion"), row.get("FBOMID"), "V1");
                        if (!StringUtils.hasText(routeCode) && !StringUtils.hasText(product)) {
                            return "SKIP";
                        }
                        String groupKey = StringUtils.hasText(routeCode)
                                ? routeCode.trim()
                                : product.trim() + "#" + version.trim();
                        grouped.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(row);
                        return "OK";
                    });
            int inserted = 0;
            int updated = 0;
            for (List<MesKingdeeRow> rows : grouped.values()) {
                String action = upsertRoute(rows);
                if ("INSERTED".equals(action)) {
                    inserted++;
                } else if ("UPDATED".equals(action)) {
                    updated++;
                }
            }
            result.setFetched(fetched);
            result.setInserted(inserted);
            result.setUpdated(updated);
            result.setSuccess(true);
            result.setMessage(String.format("工艺路线同步完成：产品版本 %d，新增 %d，更新 %d",
                    grouped.size(), inserted, updated));
            return result;
        } catch (Exception e) {
            log.error("MES route sync failed", e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            return result;
        }
    }

    public MesSyncResult syncOpPlans(String moNo) {
        MesSyncResult result = new MesSyncResult();
        result.setType("工序计划");
        if (!kingdeeCloudService.isEnabled()) {
            result.setSuccess(false);
            result.setMessage("金蝶对接未启用，请在配置中开启 kingdee.cloud.enabled");
            return result;
        }
        String filter = kingdeeProperties.getMesOpPlanFilter();
        if (StringUtils.hasText(moNo)) {
            String extra = "(FMoNumber='" + escape(moNo) + "' or FMoBillNo='" + escape(moNo) + "')";
            filter = StringUtils.hasText(filter) ? filter + " and " + extra : extra;
        }
        try {
            int fetched = queryAll(kingdeeProperties.getMesOpPlanFormId(), kingdeeProperties.getMesOpPlanFieldKeys(),
                    kingdeeProperties.getMesOpPlanAltFormId(), kingdeeProperties.getMesOpPlanAltFieldKeys(),
                    filter, row -> upsertOpPlan(row, result));
            result.setFetched(fetched);
            result.setSuccess(true);
            if (!StringUtils.hasText(result.getMessage())) {
                result.setMessage(String.format("工序计划同步完成：拉取 %d，新增 %d，更新 %d，拒绝 %d",
                        fetched, result.getInserted(), result.getUpdated(), result.getRejected()));
            }
            return result;
        } catch (Exception e) {
            log.error("MES op plan sync failed moNo={}", moNo, e);
            result.setSuccess(false);
            result.setMessage(e.getMessage() == null ? "工序计划同步失败" : e.getMessage());
            return result;
        }
    }

    private MesSyncResult pull(String type, String formId, String fieldKeys, RowUpsert upsert) {
        MesSyncResult result = new MesSyncResult();
        result.setType(type);
        if (!kingdeeCloudService.isEnabled()) {
            result.setSuccess(false);
            result.setMessage("金蝶对接未启用，请在配置中开启 kingdee.cloud.enabled");
            return result;
        }
        try {
            int fetched = queryAll(formId, fieldKeys, null, null, masterFilter(), row -> {
                String action = upsert.apply(row);
                if ("INSERTED".equals(action)) {
                    result.setInserted(result.getInserted() + 1);
                } else if ("UPDATED".equals(action)) {
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    result.setSkipped(result.getSkipped() + 1);
                }
                return action;
            });
            result.setFetched(fetched);
            result.setSuccess(true);
            result.setMessage(String.format("%s同步完成：拉取 %d，新增 %d，更新 %d，跳过 %d",
                    type, fetched, result.getInserted(), result.getUpdated(), result.getSkipped()));
            return result;
        } catch (BusinessException e) {
            log.error("MES {} sync failed: {}", type, e.getMessage());
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            return result;
        } catch (Exception e) {
            log.error("MES {} sync failed", type, e);
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            return result;
        }
    }

    private int queryAll(String formId, String fieldKeys, String filter, RowHandler handler) {
        return queryAll(formId, fieldKeys, null, null, filter, handler);
    }

    private int queryAll(String formId, String fieldKeys, String altFormId, String altFieldKeys,
                         String filter, RowHandler handler) {
        QueryTarget target = resolveQuery(formId, fieldKeys, altFormId, altFieldKeys, filter);
        int pageSize = Math.max(1, kingdeeProperties.getMasterDataQueryLimit());
        int start = 0;
        int fetched = 0;
        while (true) {
            List<List<String>> rows = kingdeeCloudService.executeBillQuery(
                    target.formId(), target.fieldKeys(), filter, "", start, pageSize);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (List<String> raw : rows) {
                fetched++;
                handler.handle(MesKingdeeRow.parse(target.fieldKeys(), raw));
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }
        return fetched;
    }

    private QueryTarget resolveQuery(String formId, String fieldKeys, String altFormId, String altFieldKeys,
                                     String filter) {
        List<QueryTarget> tries = new ArrayList<>();
        for (String candidate : MesKingdeeFormIds.candidates(formId)) {
            tries.add(new QueryTarget(candidate, fieldKeys));
        }
        if (StringUtils.hasText(altFormId)) {
            String keys = StringUtils.hasText(altFieldKeys) ? altFieldKeys : fieldKeys;
            tries.add(new QueryTarget(altFormId.trim(), keys));
        }
        BusinessException last = null;
        for (QueryTarget target : tries) {
            try {
                kingdeeCloudService.executeBillQuery(target.formId(), target.fieldKeys(), filter, "", 0, 1);
                log.info("MES ExecuteBillQuery resolved formId={}", target.formId());
                return target;
            } catch (BusinessException e) {
                last = e;
                log.warn("MES ExecuteBillQuery probe failed formId={} msg={}", target.formId(), e.getMessage());
            }
        }
        throw last != null ? last : new BusinessException(ErrorCode.INTERNAL_ERROR, "金蝶单据查询失败");
    }

    private String upsertProcess(MesKingdeeRow row) {
        String code = row.get("FNumber");
        if (!StringUtils.hasText(code)) {
            return "SKIP";
        }
        boolean active = row.isActive(kingdeeProperties.isMasterDataApprovedOnly());
        MesProcess existing = processMapper.selectOne(new LambdaQueryWrapper<MesProcess>()
                .eq(MesProcess::getProcessCode, code.trim()));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            MesProcess process = new MesProcess();
            process.setProcessCode(code.trim());
            fillProcess(process, row, active, now);
            processMapper.insert(process);
            return "INSERTED";
        }
        fillProcess(existing, row, active, now);
        processMapper.updateById(existing);
        return "UPDATED";
    }

    private void fillProcess(MesProcess process, MesKingdeeRow row, boolean active, LocalDateTime now) {
        process.setProcessName(firstNonBlank(row.get("FName"), process.getProcessCode()));
        process.setDeptCode(row.get("FDeptId.FNumber", "FWorkCenterId.FNumber"));
        process.setDeptName(row.get("FDeptId.FName", "FWorkCenterId.FName"));
        process.setReportFlag(row.flagOrDefault(MesConstants.FLAG_YES, "FIsReport"));
        process.setTransferFlag(row.flagOrDefault(MesConstants.FLAG_YES, "FIsTransfer"));
        process.setInspectFlag(row.flagOrDefault(MesConstants.FLAG_NO, "FIsQC", "FIsInspect"));
        BigDecimal ratio = row.decimal("FOverRate", "FOverReceiveRate");
        if (ratio != null) {
            process.setOverReceiveRatio(ratio);
        }
        process.setStatus(active ? MesConstants.STATUS_ACTIVE : MesConstants.STATUS_DISABLED);
        process.setSyncStatus(MesConstants.SYNC_SYNCED);
        process.setLastSyncTime(now);
        process.setFailReason(null);
    }

    private String upsertEquipment(MesKingdeeRow row) {
        String code = row.get("FNumber");
        if (!StringUtils.hasText(code)) {
            return "SKIP";
        }
        boolean active = row.isActive(kingdeeProperties.isMasterDataApprovedOnly());
        MesEquipment existing = equipmentMapper.selectOne(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getEquipmentCode, code.trim()));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            MesEquipment equipment = new MesEquipment();
            equipment.setEquipmentCode(code.trim());
            fillEquipment(equipment, row, active, now);
            equipmentMapper.insert(equipment);
            return "INSERTED";
        }
        fillEquipment(existing, row, active, now);
        equipmentMapper.updateById(existing);
        return "UPDATED";
    }

    private void fillEquipment(MesEquipment equipment, MesKingdeeRow row, boolean active, LocalDateTime now) {
        equipment.setEquipmentName(firstNonBlank(row.get("FName"), equipment.getEquipmentCode()));
        equipment.setProcessCode(row.get("FProcessId.FNumber", "FOperID.FNumber"));
        equipment.setSpecModel(row.get("FModel", "FSpecification"));
        equipment.setStatus(active ? MesConstants.STATUS_ACTIVE : MesConstants.STATUS_DISABLED);
        equipment.setSyncStatus(MesConstants.SYNC_SYNCED);
        equipment.setLastSyncTime(now);
        equipment.setFailReason(null);
    }

    private String upsertRoute(List<MesKingdeeRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return "SKIP";
        }
        MesKingdeeRow first = rows.get(0);
        String routeCode = first.get("FNumber");
        String product = first.get("FMaterialID.FNumber", "FMaterialId.FNumber", "FProductId.FNumber");
        String version = firstNonBlank(first.get("FVersion"), first.get("FBOMID"), "V1");
        if (!StringUtils.hasText(product) && !StringUtils.hasText(routeCode)) {
            return "SKIP";
        }
        boolean active = first.isActive(kingdeeProperties.isMasterDataApprovedOnly());
        MesRoute existing = null;
        if (StringUtils.hasText(routeCode)) {
            existing = routeMapper.selectOne(new LambdaQueryWrapper<MesRoute>()
                    .eq(MesRoute::getRouteCode, routeCode.trim()));
        }
        if (existing == null && StringUtils.hasText(product)) {
            existing = routeMapper.selectOne(new LambdaQueryWrapper<MesRoute>()
                    .eq(MesRoute::getProductCode, product.trim())
                    .eq(MesRoute::getVersionNo, version.trim()));
        }
        LocalDateTime now = LocalDateTime.now();
        MesRoute header = existing == null ? new MesRoute() : existing;
        header.setRouteCode(StringUtils.hasText(routeCode) ? routeCode.trim() : null);
        header.setRouteName(first.get("FName"));
        header.setProductCode(StringUtils.hasText(product) ? product.trim() : routeCode.trim());
        header.setProductName(first.get("FMaterialID.FName", "FMaterialId.FName", "FProductId.FName"));
        header.setVersionNo(version.trim());
        header.setOverReceiveRatio(first.decimal("FOverRate", "FOverReceiveRate"));
        header.setStatus(active ? MesConstants.STATUS_ACTIVE : MesConstants.STATUS_DISABLED);
        header.setSyncStatus(MesConstants.SYNC_SYNCED);
        header.setLastSyncTime(now);
        header.setFailReason(null);
        if (existing == null) {
            routeMapper.insert(header);
        } else {
            routeMapper.updateById(header);
            routeOpMapper.delete(new LambdaQueryWrapper<MesRouteOp>().eq(MesRouteOp::getRouteId, header.getId()));
        }
        int seq = 1;
        for (MesKingdeeRow row : rows) {
            String processCode = row.get("FEntity_FProcessId.FNumber", "FProcessId.FNumber", "FOperID.FNumber");
            if (!StringUtils.hasText(processCode)) {
                continue;
            }
            MesRouteOp op = new MesRouteOp();
            op.setRouteId(header.getId());
            op.setProductCode(header.getProductCode());
            op.setVersionNo(header.getVersionNo());
            Integer rowSeq = row.intVal("FEntity_FSeq", "FSeq");
            op.setSeqNo(rowSeq == null ? seq : rowSeq);
            op.setProcessCode(processCode.trim());
            op.setProcessName(row.get("FEntity_FProcessId.FName", "FProcessId.FName", "FOperID.FName"));
            op.setStdHours(stdHours(row));
            op.setInspectFlag(row.flagOrDefault(MesConstants.FLAG_NO, "FEntity_FIsQC", "FIsInspect"));
            op.setReworkJoinFlag(row.flagOrDefault(MesConstants.FLAG_NO, "FEntity_FReworkJoin", "FIsReworkJoin"));
            op.setWorkCenterCode(row.get("FEntity_FWorkCenterId.FNumber", "FWorkCenterId.FNumber"));
            routeOpMapper.insert(op);
            seq++;
        }
        return existing == null ? "INSERTED" : "UPDATED";
    }

    private String upsertOpPlan(MesKingdeeRow row, MesSyncResult result) {
        String moNo = row.get("MOBillNO", "FMOBillNO", "FMoNumber", "FMoBillNo", "FMoNo");
        String processCode = row.get("FEntity_FProcessId.FNumber", "FOperID.FNumber",
                "FProcessId.FNumber", "FOperId.FNumber");
        if (!StringUtils.hasText(moNo) || !StringUtils.hasText(processCode)) {
            result.setSkipped(result.getSkipped() + 1);
            return "SKIP";
        }
        Long entryId = row.longVal("FEntity_FEntryID", "FEntryID", "FID");
        String erpBillNo = row.get("FBillNo");
        Integer seqNo = row.intVal("FEntity_FSeq", "FOperNumber", "FSeq");
        String planKey = MesPlanKey.of(erpBillNo, entryId, moNo, processCode, seqNo);
        BigDecimal newQty = row.decimal("FEntity_FPlanQty", "FPlanQty", "FQty");
        if (newQty == null) {
            newQty = BigDecimal.ZERO;
        }
        MesOpPlan existing = opPlanMapper.selectOne(new LambdaQueryWrapper<MesOpPlan>()
                .eq(MesOpPlan::getPlanKey, planKey));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            MesOpPlan plan = new MesOpPlan();
            plan.setPlanKey(planKey);
            plan.setReportedQty(nvl(row.decimal("FEntity_FReportQty", "FReportQty", "FFinishQty")));
            plan.setReworkReportedQty(BigDecimal.ZERO);
            fillPlan(plan, row, moNo, processCode, erpBillNo, entryId, seqNo, newQty, now);
            opPlanMapper.insert(plan);
            result.setInserted(result.getInserted() + 1);
            return "INSERTED";
        }
        MesPlanChangePolicy.Decision decision = MesPlanChangePolicy.forOpPlanQty(existing.getPlanQty(), newQty);
        if (decision == MesPlanChangePolicy.Decision.REJECT) {
            existing.setChangeRejectReason(MesPlanChangePolicy.rejectOpPlanQtyMessage());
            existing.setSyncStatus(MesConstants.SYNC_SYNCED);
            existing.setLastSyncTime(now);
            fillPlanMeta(existing, row, now);
            opPlanMapper.updateById(existing);
            result.setRejected(result.getRejected() + 1);
            return "REJECT";
        }
        existing.setChangeRejectReason(null);
        fillPlan(existing, row, moNo, processCode, erpBillNo, entryId, seqNo, newQty, now);
        opPlanMapper.updateById(existing);
        result.setUpdated(result.getUpdated() + 1);
        return "UPDATED";
    }

    private void fillPlan(MesOpPlan plan, MesKingdeeRow row, String moNo, String processCode,
                          String erpBillNo, Long entryId, Integer seqNo, BigDecimal planQty, LocalDateTime now) {
        plan.setMoNo(moNo.trim());
        plan.setProcessCode(processCode.trim());
        plan.setProcessName(row.get("FEntity_FProcessId.FName", "FOperID.FName", "FProcessId.FName", "FOperId.FName"));
        plan.setProductCode(row.get("FMaterialID.FNumber", "FMaterialId.FNumber",
                "FProductId.FNumber", "FPRODUCTID.FNumber"));
        plan.setProductName(row.get("FMaterialID.FName", "FMaterialId.FName", "FProductId.FName", "FPRODUCTID.FName"));
        plan.setErpBillNo(erpBillNo);
        plan.setErpEntryId(entryId);
        plan.setSeqNo(seqNo);
        plan.setPlanQty(planQty);
        plan.setPlanStart(row.date("FEntity_FPlanStartDate", "FPlanStartDate", "FStartDate"));
        plan.setPlanEnd(row.date("FEntity_FPlanFinishDate", "FPlanFinishDate", "FFinishDate", "FPlanEndDate"));
        plan.setWorkShopCode(row.get("FWorkShopId.FNumber", "FWorkShopID.FNumber"));
        plan.setWorkShopName(row.get("FWorkShopId.FName", "FWorkShopID.FName"));
        plan.setErpStatus(row.get("FDocumentStatus"));
        plan.setPlanStatus(mapPlanStatus(row.get("FDocumentStatus"), plan.getReportedQty()));
        fillPlanMeta(plan, row, now);
    }

    private void fillPlanMeta(MesOpPlan plan, MesKingdeeRow row, LocalDateTime now) {
        plan.setOverReceiveRatio(row.decimal("FOverRate", "FOverReceiveRate"));
        plan.setSyncStatus(MesConstants.SYNC_SYNCED);
        plan.setLastSyncTime(now);
        plan.setFailReason(null);
    }

    private String mapPlanStatus(String erpStatus, BigDecimal reportedQty) {
        if ("Z".equalsIgnoreCase(erpStatus)) {
            return MesConstants.PLAN_CLOSED;
        }
        if ("D".equalsIgnoreCase(erpStatus)) {
            return MesConstants.PLAN_PAUSED;
        }
        if (reportedQty != null && reportedQty.compareTo(BigDecimal.ZERO) > 0) {
            return MesConstants.PLAN_RUNNING;
        }
        return MesConstants.PLAN_RELEASED;
    }

    private String masterFilter() {
        if (!kingdeeProperties.isMasterDataApprovedOnly()) {
            return "";
        }
        return "FDocumentStatus='C' and FForbidStatus='A'";
    }

    private static String escape(String value) {
        return value == null ? "" : value.trim().replace("'", "''");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private static BigDecimal stdHours(MesKingdeeRow row) {
        BigDecimal std = row.decimal("FEntity_FStdHour", "FStdHour", "FStdTime");
        if (std != null) {
            return std;
        }
        BigDecimal sum = nvl(row.decimal("FEntity_FPrepareTime"))
                .add(nvl(row.decimal("FEntity_FProcessTime")))
                .add(nvl(row.decimal("FEntity_FTransferTime")));
        return sum.compareTo(BigDecimal.ZERO) == 0 ? null : sum;
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record QueryTarget(String formId, String fieldKeys) {
    }

    @FunctionalInterface
    private interface RowUpsert {
        String apply(MesKingdeeRow row);
    }

    @FunctionalInterface
    private interface RowHandler {
        String handle(MesKingdeeRow row);
    }
}
