package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 金蝶云星空 WebAPI 对接（企业版）
 * 认证：AuthService.ValidateUser · 查询：ExecuteBillQuery · 保存：Save · 提交：Submit · 审核：Audit
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KingdeeCloudService {

    private final KingdeeCloudProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    /** 金蝶会话缓存，避免每次 ExecuteBillQuery / View 都 ValidateUser */
    private final Object sessionLock = new Object();
    private volatile String cachedSessionCookie;
    private volatile long sessionExpireAtMs;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 轻量探测 ERP 是否可达（用于 MES 离线判定）。
     */
    public boolean ping() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            login();
            return true;
        } catch (Exception e) {
            log.warn("Kingdee ping failed: {}", e.getMessage());
            return false;
        }
    }

    /** 是否向 PDA 返回内存模拟单据（仅本地联调） */
    public boolean isMockEnabled() {
        return properties.isMockEnabled();
    }

    /**
     * 金蝶 View 接口按内码查看单据详情。
     */
    public JsonNode viewBillById(String formId, long billId) {
        if (!properties.isEnabled()) {
            return null;
        }
        if (billId <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单据内码无效");
        }
        try {
            String session = login();
            ObjectNode data = objectMapper.createObjectNode();
            data.put("CreateOrgId", 0);
            data.put("Number", "");
            data.put("Id", String.valueOf(billId));
            data.put("IsSortBySeq", false);

            String responseJson = postWithSession(
                    "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc",
                    session,
                    root -> {
                        root.put("formid", formId);
                        root.set("data", data);
                    });
            return parseViewResult(responseJson);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Kingdee View failed formId={} billId={}", formId, billId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶单据查看失败(" + formId + "): " + e.getMessage());
        }
    }

    /**
     * 金蝶 View 接口查看单据详情（收料通知单等）。
     */
    public JsonNode viewBill(String formId, String billNo) {
        if (!properties.isEnabled()) {
            return null;
        }
        if (!StringUtils.hasText(billNo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单据编号不能为空");
        }
        try {
            String session = login();
            ObjectNode data = objectMapper.createObjectNode();
            data.put("CreateOrgId", 0);
            data.put("Number", billNo.trim());
            data.put("Id", "");
            data.put("IsSortBySeq", false);

            String responseJson = postWithSession(
                    "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.View.common.kdsvc",
                    session,
                    root -> {
                        root.put("formid", formId);
                        root.set("data", data);
                    });
            return parseViewResult(responseJson);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Kingdee View failed formId={} billNo={}", formId, billNo, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶单据查看失败(" + formId + "): " + e.getMessage());
        }
    }

    /**
     * 读取生产订单分录车间编码，供生产入库 FWorkShopId1 与 MO 行校验一致。
     */
    public String resolveMoWorkShopNumber(Long moId, String moBillNo, Long moEntryId, Integer moEntrySeq) {
        JsonNode billNode = viewMoBill(moId, moBillNo);
        if (billNode == null) {
            return null;
        }
        String workShop = KingdeeMoWorkShopParser.resolve(billNode, moEntryId, moEntrySeq);
        if (!StringUtils.hasText(workShop)) {
            log.warn("生产订单未解析到车间 moId={} moBillNo={} moEntryId={} moEntrySeq={}",
                    moId, moBillNo, moEntryId, moEntrySeq);
        }
        return workShop;
    }

    /**
     * 读取生产订单分录仓库，供生产入库自动分配（跳过单据空仓/占位仓）。
     */
    public String resolveMoStockNumber(Long moId, String moBillNo, Long moEntryId, Integer moEntrySeq) {
        JsonNode billNode = viewMoBill(moId, moBillNo);
        if (billNode == null) {
            return null;
        }
        return KingdeeMoWorkShopParser.resolveStock(billNode, moEntryId, moEntrySeq);
    }

    /**
     * 读取物料默认仓库（BD_MATERIAL 库存.仓库）。
     */
    public String resolveMaterialDefaultStockNumber(String materialCode) {
        if (!properties.isEnabled() || !StringUtils.hasText(materialCode)) {
            return null;
        }
        String formId = StringUtils.hasText(properties.getMaterialFormId())
                ? properties.getMaterialFormId() : "BD_MATERIAL";
        String number = materialCode.trim().replace("'", "''");
        try {
            List<List<String>> rows = executeBillQuery(
                    formId,
                    "FNumber,FStockId.FNumber,FMaterialStock.FStockId.FNumber",
                    "FNumber='" + number + "'",
                    "",
                    0,
                    10);
            if (rows == null || rows.isEmpty()) {
                return null;
            }
            List<String> row = rows.get(0);
            String stock = firstNonBlankCell(row, 1);
            if (!StringUtils.hasText(stock)) {
                stock = firstNonBlankCell(row, 2);
            }
            return StringUtils.hasText(stock) ? stock.trim() : null;
        } catch (BusinessException ex) {
            log.warn("查询物料默认仓库失败 material={} : {}", materialCode, ex.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("查询物料默认仓库失败 material={}", materialCode, e);
            return null;
        }
    }

    private JsonNode viewMoBill(Long moId, String moBillNo) {
        if (!properties.isEnabled()) {
            return null;
        }
        String formId = StringUtils.hasText(properties.getPrdMoFormId()) ? properties.getPrdMoFormId() : "PRD_MO";
        try {
            JsonNode billNode = null;
            if (moId != null && moId > 0) {
                billNode = viewBillById(formId, moId);
            }
            if (billNode == null && StringUtils.hasText(moBillNo)) {
                billNode = viewBill(formId, moBillNo.trim());
            }
            return billNode;
        } catch (BusinessException ex) {
            log.warn("查询生产订单失败 moId={} moBillNo={} : {}", moId, moBillNo, ex.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("查询生产订单失败 moId={} moBillNo={}", moId, moBillNo, e);
            return null;
        }
    }

    private static String firstNonBlankCell(List<String> row, int index) {
        if (row == null || index < 0 || index >= row.size()) {
            return null;
        }
        String value = row.get(index);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private JsonNode parseViewResult(String responseJson) throws Exception {
        if (!StringUtils.hasText(responseJson)) {
            return null;
        }
        String trimmed = responseJson.trim();
        if (trimmed.startsWith("response_error:")) {
            String msg = trimmed.substring("response_error:".length()).trim();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶接口返回错误: " + msg, "KINGDEE_API_ERROR");
        }
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode status = root.path("Result").path("ResponseStatus");
        if (status.isObject()) {
            JsonNode isSuccess = status.get("IsSuccess");
            boolean success = isSuccess != null
                    && ("true".equalsIgnoreCase(isSuccess.asText()) || isSuccess.asBoolean(false));
            if (!success) {
                String err = status.path("Errors").toString();
                if (!StringUtils.hasText(err) || "null".equals(err)) {
                    err = status.path("Message").asText("金蝶查看失败");
                }
                throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                        "金蝶查看失败: " + err, "KINGDEE_VIEW_FAILED");
            }
        }
        JsonNode result = root.path("Result").path("Result");
        if (result.isMissingNode() || result.isNull()) {
            return null;
        }
        if (result.isTextual()) {
            String inner = result.asText("");
            if (!StringUtils.hasText(inner) || "{}".equals(inner.trim())) {
                return null;
            }
            return objectMapper.readTree(inner);
        }
        return result;
    }

    /**
     * 查询金蝶用户列表（SEC_User：FUserID / FName / FPhone），供用户管理选择绑定。
     */
    public List<com.wms.integration.kingdee.dto.KingdeeSecUserVo> listSecUsers(String keyword, int limit) {
        int rowLimit = limit > 0 ? Math.min(limit, 2000) : 2000;
        String formId = StringUtils.hasText(properties.getSecUserFormId())
                ? properties.getSecUserFormId() : "SEC_User";
        String fieldKeys = StringUtils.hasText(properties.getSecUserFieldKeys())
                ? properties.getSecUserFieldKeys() : "FUserID,FName,FPhone";
        String filter = "";
        if (StringUtils.hasText(keyword)) {
            String key = keyword.trim().replace("'", "''");
            filter = "FName like '%" + key + "%' or FPhone like '%" + key + "%'";
        }
        List<List<String>> rows = executeBillQuery(formId, fieldKeys, filter, "", 0, rowLimit);
        List<com.wms.integration.kingdee.dto.KingdeeSecUserVo> result = new java.util.ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (List<String> row : rows) {
            if (row == null || row.isEmpty() || !StringUtils.hasText(row.get(0))) {
                continue;
            }
            try {
                long userId = Long.parseLong(row.get(0).trim());
                if (userId <= 0) {
                    continue;
                }
                com.wms.integration.kingdee.dto.KingdeeSecUserVo vo =
                        new com.wms.integration.kingdee.dto.KingdeeSecUserVo();
                vo.setUserId(userId);
                vo.setUserName(row.size() > 1 ? row.get(1) : null);
                vo.setPhone(row.size() > 2 ? row.get(2) : null);
                result.add(vo);
            } catch (NumberFormatException ignored) {
                // skip invalid row
            }
        }
        return result;
    }

    /**
     * 按金蝶用户名称解析 FUserID，用于用户映射冗余缓存。
     *
     * @param userNumber 业务侧录入的 kd_user_number（通常为 FName）
     * @return FUserID；未找到返回 null
     */
    public Long querySecUserIdByNumber(String userNumber) {
        if (!StringUtils.hasText(userNumber)) {
            return null;
        }
        if (!properties.isEnabled()) {
            return null;
        }
        String number = userNumber.trim().replace("'", "''");
        String formId = StringUtils.hasText(properties.getSecUserFormId())
                ? properties.getSecUserFormId() : "SEC_User";
        String fieldKeys = StringUtils.hasText(properties.getSecUserFieldKeys())
                ? properties.getSecUserFieldKeys() : "FUserID,FName,FPhone";
        String filter = "FName='" + number + "'";
        List<List<String>> rows = executeBillQuery(formId, fieldKeys, filter, "", 0, 10);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (List<String> row : rows) {
            if (row == null || row.isEmpty() || !StringUtils.hasText(row.get(0))) {
                continue;
            }
            try {
                long id = Long.parseLong(row.get(0).trim());
                if (id > 0) {
                    return id;
                }
            } catch (NumberFormatException ignored) {
                // try next row
            }
        }
        return null;
    }

    /**
     * 金蝶 ExecuteBillQuery，成功时返回行数据（每行对应 FieldKeys 顺序的字符串列表）。
     */
    public List<List<String>> executeBillQuery(String formId, String fieldKeys, String filterString,
                                               String orderString, int startRow, int limit) {
        if (!properties.isEnabled()) {
            return List.of();
        }
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("FormId", formId);
            data.put("FieldKeys", fieldKeys);
            // 与金蝶文档一致：无条件时传空数组
            if (StringUtils.hasText(filterString)) {
                data.put("FilterString", filterString);
            } else {
                data.putArray("FilterString");
            }
            data.put("OrderString", orderString == null ? "" : orderString);
            data.put("TopRowCount", 0);
            data.put("StartRow", startRow);
            data.put("Limit", limit);
            data.put("SubSystemId", "");

            String session = login();
            String responseJson = postWithSession(
                    "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc",
                    session,
                    root -> root.set("data", data));
            if (isSessionExpiredResponse(responseJson)) {
                invalidateSession();
                session = login();
                responseJson = postWithSession(
                        "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.ExecuteBillQuery.common.kdsvc",
                        session,
                        root -> root.set("data", data));
            }
            return parseBillQueryRows(responseJson);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            log.error("Kingdee ExecuteBillQuery failed formId={} filter={}", formId, filterString, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶单据查询失败(" + formId + "): " + e.getMessage());
        }
    }

    public KingdeeSyncResult syncStockIn(KingdeeStockInRequest req) {
        String erpWarehouse = StringUtils.hasText(req.getErpWarehouseCode())
                ? req.getErpWarehouseCode() : req.getWarehouseCode();
        KingdeePurchaseInStockRequest purchaseReq = KingdeePurchaseInStockRequest.builder()
                .sourceBillNo(req.getSourceBillNo())
                .sourceBillType(StringUtils.hasText(req.getSourceBillType())
                        ? req.getSourceBillType() : "PUR_ReceiveBill")
                .supplierCode(req.getSupplierCode())
                .lines(List.of(KingdeePurchaseInStockRequest.Line.builder()
                        .materialCode(req.getMaterialCode())
                        .materialName(req.getMaterialName())
                        .unitCode(req.getUnitCode())
                        .warehouseCode(erpWarehouse)
                        .locationCode(req.getLocationCode())
                        .batchNo(req.getBatchNo())
                        .quantity(req.getQuantity())
                        .sourceLineNo(req.getSourceLineNo())
                        .sourceBillId(req.getSourceBillId())
                        .sourceEntryId(req.getSourceEntryId())
                        .build()))
                .build();
        return syncPurchaseInStock(purchaseReq);
    }

    /**
     * 直接 Save 创建采购入库单（含 FInStockEntry_Link 收料通知单关联）。
     */
    public KingdeeSyncResult syncPurchaseInStock(KingdeePurchaseInStockRequest req) {
        String savePayload = KingdeePurchaseInStockBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getSourceBillNo();
        String sendBillField = KingdeePurchaseInStockBuilder.SEND_BILL_NO_FIELD;
        boolean payloadHasSendBillNo = savePayload != null
                && savePayload.contains("\"" + sendBillField + "\"");
        String sampleSendBillNo = null;
        if (req.getLines() != null) {
            for (KingdeePurchaseInStockRequest.Line line : req.getLines()) {
                if (line != null && StringUtils.hasText(line.getSendBillNo())) {
                    sampleSendBillNo = line.getSendBillNo().trim();
                    break;
                }
            }
        }
        log.info("Kingdee purchase in-stock payload ready batchNo={} sourceBillNo={} hasSendBillNoField={} field={} sendBillNo={}",
                logKey, req.getSourceBillNo(), payloadHasSendBillNo, sendBillField, sampleSendBillNo);
        if (!payloadHasSendBillNo) {
            log.error("Kingdee purchase in-stock payload missing {}, batchNo={}", sendBillField, logKey);
        }
        if (!properties.isEnabled()) {
            log.info("Kingdee mock purchase in-stock save for {}", logKey);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo("CGRK-MOCK-" + (logKey != null ? logKey : "BATCH"))
                    .submitted(properties.isStockInAutoAudit())
                    .audited(properties.isStockInAutoAudit())
                    .message(properties.isStockInAutoAudit()
                            ? "模拟保存、提交并审核成功（kingdee.cloud.enabled=false）"
                            : "模拟同步成功（kingdee.cloud.enabled=false）")
                    .requestJson(savePayload)
                    .responseJson("{\"Result\":{\"ResponseStatus\":{\"IsSuccess\":true},\"Number\":\"CGRK-MOCK\"}}")
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, properties.getStockInFormId(), savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess()) {
                return saveResult;
            }
            if (!properties.isStockInAutoAudit()) {
                return saveResult;
            }
            return submitAndAuditAfterSave(session, properties.getStockInFormId(), saveResult);
        } catch (Exception e) {
            log.error("Kingdee purchase in-stock save failed for {}", logKey, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .responseJson(null)
                    .build();
        }
    }

    /**
     * 原始 Save 联调：直接提交金蝶 Save 报文，可选 Submit + Audit。
     *
     * @param formId   单据 FormId，如 PRD_PickMtrl、PRD_INSTOCK
     * @param dataJson Save 接口 data 字段 JSON（含 NeedUpDateFields / Model）
     * @param autoAudit 保存成功后是否自动提交并审核
     */
    public KingdeeSyncResult rawSave(String formId, String dataJson, boolean autoAudit) {
        if (!StringUtils.hasText(formId)) {
            return KingdeeSyncResult.builder().success(false).message("formId 不能为空").build();
        }
        if (!StringUtils.hasText(dataJson)) {
            return KingdeeSyncResult.builder().success(false).message("Save 报文不能为空").build();
        }
        String payload = dataJson.trim();
        if (!properties.isEnabled()) {
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo("RAW-MOCK-" + formId)
                    .submitted(autoAudit)
                    .audited(autoAudit)
                    .message("模拟 rawSave 成功（kingdee.cloud.enabled=false） formId=" + formId)
                    .requestJson(payload)
                    .responseJson("{\"Result\":{\"ResponseStatus\":{\"IsSuccess\":true},\"Number\":\"RAW-MOCK\"}}")
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId.trim(), payload);
            KingdeeSyncResult saveResult = parseSaveResponse(payload, saveResponseJson);
            if (!saveResult.isSuccess() || !autoAudit) {
                return saveResult;
            }
            return submitAndAuditAfterSave(session, formId.trim(), saveResult);
        } catch (Exception e) {
            log.error("Kingdee rawSave failed formId={}", formId, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestJson(payload)
                    .build();
        }
    }

    public KingdeeSyncResult syncPrdInStock(KingdeePrdInStockRequest req) {
        String savePayload = KingdeePrdInStockBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : "PRD_IN";
        return syncBuiltPayload(properties.getPrdInStockFormId(), savePayload, logKey,
                properties.isPrdInStockAutoAudit(), "生产入库", "SCRK-MOCK-");
    }

    public KingdeeSyncResult syncMiscInStock(KingdeeMiscInStockRequest req) {
        String savePayload = KingdeeMiscInStockBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : "MISC_IN";
        return syncBuiltPayload(properties.getMiscInStockFormId(), savePayload, logKey,
                properties.isMiscInStockAutoAudit(), "其他入库", "QTRK-MOCK-");
    }

    public KingdeeSyncResult syncMisDelivery(KingdeeMisDeliveryRequest req) {
        String savePayload = KingdeeMisDeliveryBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : "MISC_OUT";
        return syncBuiltPayload(properties.getMisDeliveryFormId(), savePayload, logKey,
                properties.isMisDeliveryAutoAudit(), "其他出库", "QTCK-MOCK-");
    }

    public KingdeeSyncResult syncSalReturnStock(KingdeeSalReturnStockRequest req) {
        String savePayload = KingdeeSalReturnStockBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getSourceBillNo();
        return syncBuiltPayload(properties.getSalReturnStockFormId(), savePayload, logKey,
                properties.isSalReturnStockAutoAudit(), "销售退货", "XSTH-MOCK-");
    }

    private KingdeeSyncResult syncBuiltPayload(String formId, String savePayload, String logKey,
                                               boolean autoAudit, String bizName, String mockPrefix) {
        if (!properties.isEnabled()) {
            log.info("Kingdee mock {} save for {}", bizName, logKey);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo(mockPrefix + (logKey != null ? logKey : "BATCH"))
                    .submitted(autoAudit)
                    .audited(autoAudit)
                    .message("模拟" + bizName + "同步成功（kingdee.cloud.enabled=false）")
                    .requestJson(savePayload)
                    .responseJson("{\"Result\":{\"ResponseStatus\":{\"IsSuccess\":true},\"Number\":\"" + mockPrefix + "\"}}")
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId, savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess() || !autoAudit) {
                return saveResult;
            }
            return submitAndAuditAfterSave(session, formId, saveResult);
        } catch (Exception e) {
            log.error("Kingdee {} save failed for {}", bizName, logKey, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .build();
        }
    }

    /**
     * 回写已有领料/补料单分录实发数量后 Submit + Audit。
     */
    public KingdeeSyncResult savePickMtrlActualQtyThenAudit(String formId, Long billId, String billNo,
                                                             List<KingdeePickMtrlActualQtyBuilder.Line> lines) {
        return savePickMtrlActualQtyThenAudit(formId, billId, billNo, lines, false, null, null, true);
    }

    public KingdeeSyncResult savePickMtrlActualQtyThenAudit(String formId, Long billId, String billNo,
                                                             List<KingdeePickMtrlActualQtyBuilder.Line> lines,
                                                             boolean useWorkflowAudit) {
        return savePickMtrlActualQtyThenAudit(formId, billId, billNo, lines, useWorkflowAudit, null, null, true);
    }

    public KingdeeSyncResult savePickMtrlActualQtyThenAudit(String formId, Long billId, String billNo,
                                                             List<KingdeePickMtrlActualQtyBuilder.Line> lines,
                                                             boolean useWorkflowAudit,
                                                             Long kingdeeUserId, String kingdeeUserName) {
        return savePickMtrlActualQtyThenAudit(formId, billId, billNo, lines,
                useWorkflowAudit, kingdeeUserId, kingdeeUserName, true);
    }

    public KingdeeSyncResult savePickMtrlActualQtyThenAudit(String formId, Long billId, String billNo,
                                                             List<KingdeePickMtrlActualQtyBuilder.Line> lines,
                                                             boolean useWorkflowAudit,
                                                             Long kingdeeUserId, String kingdeeUserName,
                                                             boolean auditAfterSave) {
        return saveEntryQtyThenAudit(formId, billId, billNo,
                KingdeePickMtrlActualQtyBuilder.build(objectMapper, billId, billNo, lines),
                "实发", useWorkflowAudit, kingdeeUserId, kingdeeUserName, auditAfterSave);
    }

    /**
     * 回写已有退料单分录实退数量；默认再 Submit + Audit（含部分退料）。
     */
    public KingdeeSyncResult saveReturnMtrlActualQtyThenAudit(String formId, Long billId, String billNo,
                                                               List<KingdeeReturnMtrlActualQtyBuilder.Line> lines) {
        return saveReturnMtrlActualQtyThenAudit(formId, billId, billNo, lines, true);
    }

    public KingdeeSyncResult saveReturnMtrlActualQtyThenAudit(String formId, Long billId, String billNo,
                                                               List<KingdeeReturnMtrlActualQtyBuilder.Line> lines,
                                                               boolean auditAfterSave) {
        return saveEntryQtyThenAudit(formId, billId, billNo,
                KingdeeReturnMtrlActualQtyBuilder.build(objectMapper, billId, billNo, lines),
                "退料实退", false, null, null, auditAfterSave);
    }

    /**
     * 采购退料单：按 PDA 实退数量回写 FRMREALQTY 后 Submit + Audit。
     */
    public KingdeeSyncResult savePurMrbActualQtyThenAudit(String formId, Long billId, String billNo,
                                                          List<KingdeePurMrbActualQtyBuilder.Line> lines,
                                                          boolean auditAfterSave) {
        return saveEntryQtyThenAudit(formId, billId, billNo,
                KingdeePurMrbActualQtyBuilder.build(objectMapper, billId, billNo, lines),
                "采购退料实退", false, null, null, auditAfterSave);
    }

    private KingdeeSyncResult saveEntryQtyThenAudit(String formId, Long billId, String billNo,
                                                    String savePayload, String bizLabel,
                                                    boolean useWorkflowAudit,
                                                    Long kingdeeUserId, String kingdeeUserName,
                                                    boolean auditAfterSave) {
        if (!StringUtils.hasText(formId) || !StringUtils.hasText(billNo)) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message("formId/billNo 不能为空")
                    .build();
        }
        if (!StringUtils.hasText(savePayload)) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(billNo.trim())
                    .message("缺少可回写的" + bizLabel + "数量")
                    .build();
        }
        String no = billNo.trim();
        if (!properties.isEnabled()) {
            log.info("Kingdee mock {} save billNo={} audit={} workflow={}",
                    bizLabel, no, auditAfterSave, useWorkflowAudit);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo(no)
                    .submitted(auditAfterSave)
                    .audited(auditAfterSave)
                    .message(auditAfterSave
                            ? "模拟" + bizLabel + "回写并审核成功（kingdee.cloud.enabled=false）: " + no
                            : "模拟" + bizLabel + "已回写（未满暂不审核）: " + no)
                    .requestJson(savePayload)
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId, savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess()) {
                return saveResult;
            }
            if (!auditAfterSave) {
                return KingdeeSyncResult.builder()
                        .success(true)
                        .billNo(no)
                        .submitted(false)
                        .audited(false)
                        .message(bizLabel + "已回写（未满暂不审核）: " + no)
                        .requestJson(savePayload)
                        .responseJson(saveResult.getResponseJson())
                        .build();
            }
            Long resolvedId = billId;
            if (resolvedId == null || resolvedId <= 0) {
                String idText = extractBillId(saveResult.getResponseJson());
                if (StringUtils.hasText(idText)) {
                    try {
                        resolvedId = Long.parseLong(idText.trim());
                    } catch (Exception ignored) {
                        // keep original
                    }
                }
            }
            KingdeeSyncResult auditResult = submitAndAuditExistingBill(
                    formId, no, resolvedId, useWorkflowAudit, kingdeeUserId, kingdeeUserName);
            return KingdeeSyncResult.builder()
                    .success(auditResult.isSuccess())
                    .billNo(no)
                    .submitted(auditResult.isSubmitted())
                    .audited(auditResult.isAudited())
                    .message(auditResult.isSuccess()
                            ? bizLabel + (useWorkflowAudit ? "已回写并工作流审批: " : "已回写并提交审核: ") + no
                            : auditResult.getMessage())
                    .requestJson(savePayload)
                    .responseJson(saveResult.getResponseJson())
                    .submitRequestJson(auditResult.getSubmitRequestJson())
                    .submitResponseJson(auditResult.getSubmitResponseJson())
                    .auditRequestJson(auditResult.getAuditRequestJson())
                    .auditResponseJson(auditResult.getAuditResponseJson())
                    .build();
        } catch (Exception e) {
            log.error("Kingdee {} save+audit failed formId={} billNo={}", bizLabel, formId, no, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .build();
        }
    }

    /**
     * 回写物料盘点作业实盘数量后 Submit + Audit。
     */
    public KingdeeSyncResult saveStockCountQtyThenSubmitAudit(Long billId, String billNo,
                                                               List<KingdeeStockCountCountQtyBuilder.Line> lines) {
        String formId = properties.getStockCountFormId();
        if (!StringUtils.hasText(formId) || !StringUtils.hasText(billNo)) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message("formId/billNo 不能为空")
                    .build();
        }
        String no = billNo.trim();
        String savePayload = KingdeeStockCountCountQtyBuilder.build(objectMapper, billId, no, lines);
        if (!properties.isEnabled()) {
            log.info("Kingdee mock stock count save+audit billNo={}", no);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo(no)
                    .submitted(true)
                    .audited(true)
                    .message("模拟盘点回写并审核成功（kingdee.cloud.enabled=false）: " + no)
                    .requestJson(savePayload)
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId, savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess()) {
                return saveResult;
            }
            Long resolvedId = billId;
            if (resolvedId == null || resolvedId <= 0) {
                String idText = extractBillId(saveResult.getResponseJson());
                if (StringUtils.hasText(idText)) {
                    try {
                        resolvedId = Long.parseLong(idText.trim());
                    } catch (Exception ignored) {
                        // keep original
                    }
                }
            }
            KingdeeSyncResult auditResult = submitAndAuditExistingBill(formId, no, resolvedId);
            return KingdeeSyncResult.builder()
                    .success(auditResult.isSuccess())
                    .billNo(no)
                    .submitted(auditResult.isSubmitted())
                    .audited(auditResult.isAudited())
                    .message(auditResult.isSuccess()
                            ? "盘点数量已回写并提交审核: " + no
                            : auditResult.getMessage())
                    .requestJson(savePayload)
                    .responseJson(saveResult.getResponseJson())
                    .submitRequestJson(auditResult.getSubmitRequestJson())
                    .submitResponseJson(auditResult.getSubmitResponseJson())
                    .auditRequestJson(auditResult.getAuditRequestJson())
                    .auditResponseJson(auditResult.getAuditResponseJson())
                    .build();
        } catch (Exception e) {
            log.error("Kingdee stock count save+audit failed billNo={}", no, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .build();
        }
    }

    /**
     * 销售发货通知下推销售出库后 Submit + Audit。
     * 若下推已带出批号/仓库，补全匹配失败时跳过 Save，直接提交审核。
     */
    public KingdeeSyncResult pushSalesDeliveryToOutStockThenAudit(String deliveryBillNo, Long deliveryBillId) {
        return pushSalesDeliveryToOutStockThenAudit(deliveryBillNo, deliveryBillId, null);
    }

    public KingdeeSyncResult pushSalesDeliveryToOutStockThenAudit(String deliveryBillNo, Long deliveryBillId,
                                                                   List<KingdeeSalOutStockEntryFill> entryFills) {
        return pushSalesDeliveryToOutStockThenAudit(deliveryBillNo, deliveryBillId, entryFills, null);
    }

    /**
     * @param existingOutStockNo 上次同步已下推出的销售出库单号（含暂存态）。有值时复用该单继续补全 + 提交审核，
     *                           不再重复 Push，避免重试在金蝶堆积多张重复的暂存出库单。
     */
    public KingdeeSyncResult pushSalesDeliveryToOutStockThenAudit(String deliveryBillNo, Long deliveryBillId,
                                                                   List<KingdeeSalOutStockEntryFill> entryFills,
                                                                   String existingOutStockNo) {
        String srcFormId = "SAL_DELIVERYNOTICE";
        String targetFormId = properties.getSalOutStockFormId();
        String ruleId = properties.getSalesDeliveryPushRuleId();
        if (!StringUtils.hasText(deliveryBillNo)) {
            return KingdeeSyncResult.builder().success(false).message("发货通知单号不能为空").build();
        }
        String no = deliveryBillNo.trim();
        if (!properties.isEnabled()) {
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo("XSCK-MOCK-" + no)
                    .submitted(true)
                    .audited(true)
                    .message("模拟发货通知下推销售出库并审核成功: " + no)
                    .build();
        }
        if (!StringUtils.hasText(ruleId)) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .message("未配置发货通知下推规则 sales-delivery-push-rule-id")
                    .build();
        }
        try {
            String session = login();
            String pushRequestJson = null;
            String pushResponseJson = null;
            String outStockNo = null;
            Long outStockIdLong = null;

            if (StringUtils.hasText(existingOutStockNo) && !existingOutStockNo.trim().equalsIgnoreCase(no)) {
                String reuseNo = existingOutStockNo.trim();
                JsonNode reuseNode = null;
                // 历史失败可能把金蝶内码误存为 erpBillNo，纯数字时按 Id 查看
                Long reuseId = parseLongQuiet(reuseNo);
                if (reuseId != null && reuseId > 0 && reuseNo.matches("^\\d{6,}$")) {
                    try {
                        reuseNode = viewBillById(targetFormId, reuseId);
                    } catch (Exception ex) {
                        log.warn("Reuse sal-outstock by Id failed. deliveryBillNo={} outStockId={}", no, reuseId, ex);
                    }
                }
                if (reuseNode == null && !isPlaceholderBillNo(reuseNo) && !reuseNo.matches("^\\d{6,}$")) {
                    try {
                        reuseNode = viewBill(targetFormId, reuseNo);
                    } catch (Exception ex) {
                        log.warn("Reuse sal-outstock by Number failed. deliveryBillNo={} outStockNo={}", no, reuseNo, ex);
                    }
                }
                if (reuseNode != null) {
                    outStockNo = firstNonBlank(
                            firstText(reuseNode, "FBillNo", "BillNo", "Number"),
                            isPlaceholderBillNo(reuseNo) || reuseNo.matches("^\\d{6,}$") ? null : reuseNo);
                    outStockIdLong = parseLongQuiet(firstText(reuseNode, "FID", "Id"));
                    if (outStockIdLong == null && reuseId != null) {
                        outStockIdLong = reuseId;
                    }
                    log.info("Reuse existing sal-outstock instead of re-push. deliveryBillNo={} outStockNo={} outStockId={}",
                            no, outStockNo, outStockIdLong);
                } else {
                    log.warn("Existing sal-outstock not found, fallback to push. deliveryBillNo={} outStockNo={}",
                            no, reuseNo);
                }
            }

            if (!StringUtils.hasText(outStockNo) && outStockIdLong == null) {
                ObjectNode pushData = objectMapper.createObjectNode();
                if (deliveryBillId != null && deliveryBillId > 0) {
                    pushData.put("Ids", String.valueOf(deliveryBillId));
                } else {
                    pushData.putArray("Numbers").add(no);
                }
                String entryIds = joinSourceEntryIds(entryFills);
                if (StringUtils.hasText(entryIds)) {
                    pushData.put("EntryIds", entryIds);
                }
                pushData.put("RuleId", ruleId.trim());
                pushData.put("TargetFormId", targetFormId);
                pushData.put("IsEnableDefaultRule", "false");
                // 批号/仓库缺失时允许暂存草稿，随后 Save 补全再审核
                pushData.put("IsDraftWhenSaveFail", "true");
                pushRequestJson = objectMapper.writeValueAsString(pushData);
                pushResponseJson = invokePush(session, srcFormId, pushRequestJson);
                KingdeeSyncResult pushResult = parseSaveResponse(pushRequestJson, pushResponseJson);
                outStockIdLong = parseLongQuiet(extractBillId(pushResponseJson));
                outStockNo = normalizePushedBillNo(pushResult.getBillNo(), outStockIdLong);
                if ((!StringUtils.hasText(outStockNo) || isPlaceholderBillNo(outStockNo)) && outStockIdLong != null) {
                    outStockNo = resolveBillNoByView(targetFormId, outStockIdLong);
                }
                if (!pushResult.isSuccess() && outStockIdLong == null) {
                    return KingdeeSyncResult.builder()
                            .success(false)
                            .billNo(no)
                            .message("发货通知下推销售出库失败: " + pushResult.getMessage())
                            .requestJson(pushRequestJson)
                            .responseJson(pushResponseJson)
                            .build();
                }
                if (outStockIdLong == null && !StringUtils.hasText(outStockNo)) {
                    return KingdeeSyncResult.builder()
                            .success(false)
                            .billNo(no)
                            .message("下推未返回销售出库单号/内码: " + pushResult.getMessage())
                            .requestJson(pushRequestJson)
                            .responseJson(pushResponseJson)
                            .build();
                }
            }

            if (entryFills != null && !entryFills.isEmpty()) {
                KingdeeSyncResult fillResult = fillSalOutStockLotAndStock(
                        session, targetFormId, outStockIdLong, outStockNo, entryFills);
                if (fillResult.isSuccess()) {
                    if (StringUtils.hasText(fillResult.getBillNo())) {
                        outStockNo = fillResult.getBillNo();
                    }
                    Long filledId = parseLongQuiet(extractBillId(fillResult.getResponseJson()));
                    if (filledId != null) {
                        outStockIdLong = filledId;
                    }
                } else {
                    // 下推常已带出批号/仓库，View 对不上 PDA 行时仍尝试审核；金蝶能审过则视为成功
                    log.warn("Sal-outstock lot/stock fill skipped, try submit+audit. deliveryBillNo={} outStockNo={} reason={}",
                            no, outStockNo, fillResult.getMessage());
                    if ((!StringUtils.hasText(outStockNo) || isPlaceholderBillNo(outStockNo)) && outStockIdLong != null) {
                        outStockNo = resolveBillNoByView(targetFormId, outStockIdLong);
                    }
                    if (StringUtils.hasText(outStockNo)) {
                        KingdeeSyncResult auditAfterFillFail = submitAndAuditExistingBill(
                                targetFormId, outStockNo, outStockIdLong);
                        if (auditAfterFillFail.isSuccess()) {
                            return KingdeeSyncResult.builder()
                                    .success(true)
                                    .billNo(outStockNo)
                                    .submitted(auditAfterFillFail.isSubmitted())
                                    .audited(auditAfterFillFail.isAudited())
                                    .message("发货通知已下推销售出库并审核: " + outStockNo)
                                    .requestJson(firstNonBlank(fillResult.getRequestJson(), pushRequestJson))
                                    .responseJson(firstNonBlank(fillResult.getResponseJson(), pushResponseJson))
                                    .submitRequestJson(auditAfterFillFail.getSubmitRequestJson())
                                    .submitResponseJson(auditAfterFillFail.getSubmitResponseJson())
                                    .auditRequestJson(auditAfterFillFail.getAuditRequestJson())
                                    .auditResponseJson(auditAfterFillFail.getAuditResponseJson())
                                    .build();
                        }
                        log.warn("Sal-outstock audit after fill-skip also failed. outStockNo={} audit={}",
                                outStockNo, auditAfterFillFail.getMessage());
                    }
                    return KingdeeSyncResult.builder()
                            .success(false)
                            .billNo(outStockNo)
                            .message("已生成销售出库单 " + outStockNo + "，但批号/仓库补全失败："
                                    + fillResult.getMessage() + "。重新提交将继续沿用该单，不会重复生成")
                            .requestJson(firstNonBlank(fillResult.getRequestJson(), pushRequestJson))
                            .responseJson(firstNonBlank(fillResult.getResponseJson(), pushResponseJson))
                            .build();
                }
            }

            if ((!StringUtils.hasText(outStockNo) || isPlaceholderBillNo(outStockNo)) && outStockIdLong != null) {
                outStockNo = resolveBillNoByView(targetFormId, outStockIdLong);
            }
            if (!StringUtils.hasText(outStockNo)) {
                return KingdeeSyncResult.builder()
                        .success(false)
                        .billNo(outStockIdLong != null ? String.valueOf(outStockIdLong) : no)
                        .message("销售出库单号为空，无法提交审核"
                                + (outStockIdLong != null ? "（已取得内码 " + outStockIdLong + "，请到金蝶核对暂存出库单）" : ""))
                        .requestJson(pushRequestJson)
                        .responseJson(pushResponseJson)
                        .build();
            }
            KingdeeSyncResult auditResult = submitAndAuditExistingBill(
                    targetFormId, outStockNo, outStockIdLong);
            return KingdeeSyncResult.builder()
                    .success(auditResult.isSuccess())
                    .billNo(outStockNo)
                    .submitted(auditResult.isSubmitted())
                    .audited(auditResult.isAudited())
                    .message(auditResult.isSuccess()
                            ? "发货通知已下推销售出库并审核: " + outStockNo
                            : auditResult.getMessage())
                    .requestJson(pushRequestJson)
                    .responseJson(pushResponseJson)
                    .submitRequestJson(auditResult.getSubmitRequestJson())
                    .submitResponseJson(auditResult.getSubmitResponseJson())
                    .auditRequestJson(auditResult.getAuditRequestJson())
                    .auditResponseJson(auditResult.getAuditResponseJson())
                    .build();
        } catch (Exception e) {
            log.error("Kingdee push sales delivery to out-stock failed billNo={}", no, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .message(e.getMessage())
                    .build();
        }
    }

    private KingdeeSyncResult fillSalOutStockLotAndStock(String session, String formId, Long billId, String billNo,
                                                         List<KingdeeSalOutStockEntryFill> fills) throws Exception {
        JsonNode billNode = null;
        if (billId != null && billId > 0) {
            try {
                billNode = viewBillById(formId, billId);
            } catch (BusinessException ex) {
                log.warn("View sal-outstock by Id failed formId={} billId={}: {}", formId, billId, ex.getMessage());
            }
        }
        if (billNode == null && StringUtils.hasText(billNo) && !isPlaceholderBillNo(billNo)) {
            try {
                billNode = viewBill(formId, billNo.trim());
            } catch (BusinessException ex) {
                log.warn("View sal-outstock by Number failed formId={} billNo={}: {}", formId, billNo, ex.getMessage());
            }
        }
        if (billNode == null) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message("无法 View 销售出库单以补全批号/仓库"
                            + (billId != null ? "（内码=" + billId + "）" : "")
                            + (StringUtils.hasText(billNo) ? "（单号=" + billNo + "）" : ""))
                    .build();
        }
        String resolvedNo = firstText(billNode, "FBillNo", "BillNo", "Number");
        Long resolvedId = parseLongQuiet(firstText(billNode, "FID", "Id"));
        if (resolvedId == null) {
            resolvedId = billId;
        }
        if (KingdeeSalOutStockEntryMatcher.alreadyHasLotAndStock(billNode)) {
            log.info("Sal-outstock already has lot/stock after push, skip fill. billNo={}", resolvedNo);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo(resolvedNo)
                    .message("下推已带出批号/仓库，跳过补全")
                    .build();
        }
        List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> updates =
                KingdeeSalOutStockEntryMatcher.match(billNode, fills);
        if (updates.isEmpty()) {
            log.warn("Sal-outstock entry match failed. billNo={} outStockMaterials={} pdaFills={}",
                    resolvedNo, KingdeeSalOutStockEntryMatcher.describeEntries(billNode),
                    KingdeeSalOutStockEntryMatcher.describeFills(fills));
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(resolvedNo)
                    .message("销售出库分录与 PDA 扫码行未能匹配，无法补全批号/仓库")
                    .build();
        }
        String savePayload = KingdeeSalOutStockLotStockBuilder.build(
                objectMapper, resolvedId, resolvedNo, updates);
        String saveResponseJson = invokeSave(session, formId, savePayload);
        KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
        if (!saveResult.isSuccess()) {
            return saveResult;
        }
        // Save 返回成功不等于字段已落库（字段名错误时常见）；再 View 校验批号/仓库
        JsonNode afterSave = null;
        Long idAfter = parseLongQuiet(extractBillId(saveResponseJson));
        if (idAfter == null) {
            idAfter = resolvedId;
        }
        try {
            if (idAfter != null && idAfter > 0) {
                afterSave = viewBillById(formId, idAfter);
            } else if (StringUtils.hasText(resolvedNo)) {
                afterSave = viewBill(formId, resolvedNo.trim());
            }
        } catch (Exception ex) {
            log.warn("Re-view sal-outstock after lot/stock save failed billNo={}", resolvedNo, ex);
        }
        if (afterSave != null && !KingdeeSalOutStockEntryMatcher.alreadyHasLotAndStock(afterSave)) {
            log.warn("Sal-outstock still missing lot/stock after Save. billNo={} entries={}",
                    resolvedNo, KingdeeSalOutStockEntryMatcher.describeEntries(afterSave));
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(resolvedNo)
                    .message("批号/仓库 Save 后仍为空，请核对物料批号与仓库编码。分录="
                            + KingdeeSalOutStockEntryMatcher.describeEntries(afterSave)
                            + "；PDA补全=" + KingdeeSalOutStockEntryMatcher.describeFills(fills))
                    .requestJson(savePayload)
                    .responseJson(saveResponseJson)
                    .build();
        }
        String billNoOut = firstNonBlank(saveResult.getBillNo(), resolvedNo);
        return KingdeeSyncResult.builder()
                .success(true)
                .billNo(billNoOut)
                .requestJson(savePayload)
                .responseJson(saveResponseJson)
                .build();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String joinSourceEntryIds(List<KingdeeSalOutStockEntryFill> fills) {
        if (fills == null || fills.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (KingdeeSalOutStockEntryFill fill : fills) {
            if (fill == null || fill.getSourceEntryId() == null || fill.getSourceEntryId() <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(fill.getSourceEntryId());
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private String resolveBillNoByView(String formId, long billId) {
        try {
            JsonNode node = viewBillById(formId, billId);
            return firstText(node, "FBillNo", "BillNo", "Number");
        } catch (Exception e) {
            log.warn("View billNo after push failed formId={} billId={}", formId, billId, e);
            return null;
        }
    }

    /** Push/Save 解析出的假单号或把内码当成 Number 时，不能拿去 View(Number=...) */
    private static boolean isPlaceholderBillNo(String billNo) {
        if (!StringUtils.hasText(billNo)) {
            return true;
        }
        String no = billNo.trim();
        return no.regionMatches(true, 0, "KD-", 0, 3) || no.matches("^\\d{6,}$");
    }

    private static String normalizePushedBillNo(String billNo, Long billId) {
        if (!StringUtils.hasText(billNo)) {
            return null;
        }
        String no = billNo.trim();
        if (no.regionMatches(true, 0, "KD-", 0, 3)) {
            return null;
        }
        // SuccessEntitys.Number 有时会回填成 Id
        if (billId != null && billId > 0 && no.equals(String.valueOf(billId))) {
            return null;
        }
        if (no.matches("^\\d{6,}$")) {
            return null;
        }
        return no;
    }

    private static Long parseLongQuiet(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim().split("\\.")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull() && !child.isMissingNode()) {
                String text = child.asText();
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private static String refNumber(JsonNode parent, String... names) {
        if (parent == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode ref = parent.get(name);
            if (ref == null || ref.isNull()) {
                continue;
            }
            if (ref.isTextual() || ref.isNumber()) {
                String t = ref.asText();
                if (StringUtils.hasText(t)) {
                    return t.trim();
                }
            }
            String num = firstText(ref, "FNumber", "FNUMBER", "Number");
            if (StringUtils.hasText(num)) {
                return num.trim();
            }
        }
        return null;
    }

    /**
     * 对已存在的金蝶单据执行 Submit + 普通 Audit。
     * 若单据已是审核中(B)/已审核(C)，跳过 Submit（已审核则直接成功）；Submit 报「只有暂存才允许提交」时同样继续 Audit。
     */
    public KingdeeSyncResult submitAndAuditExistingBill(String formId, String billNo, Long billId) {
        return submitAndAuditExistingBill(formId, billNo, billId, false, null, null);
    }

    /**
     * 对已存在的金蝶单据执行 Submit + 审批。
     * {@code useWorkflowAudit=true} 时走 WorkflowAudit（ApprovalType=1 通过），否则普通 Audit。
     * 当前生产补料、委外补料使用工作流审批。
     */
    public KingdeeSyncResult submitAndAuditExistingBill(String formId, String billNo, Long billId,
                                                        boolean useWorkflowAudit) {
        return submitAndAuditExistingBill(formId, billNo, billId, useWorkflowAudit, null, null);
    }

    /**
     * 对已存在的金蝶单据执行 Submit + 审批。
     * 工作流审批时必须传入金蝶审批人 {@code kingdeeUserId}（写入 WorkflowAudit.UserId）。
     */
    public KingdeeSyncResult submitAndAuditExistingBill(String formId, String billNo, Long billId,
                                                        boolean useWorkflowAudit,
                                                        Long kingdeeUserId, String kingdeeUserName) {
        if (!StringUtils.hasText(formId) || !StringUtils.hasText(billNo)) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message("formId/billNo 不能为空")
                    .build();
        }
        String no = billNo.trim();
        if (useWorkflowAudit && (kingdeeUserId == null || kingdeeUserId <= 0)) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .message("工作流审批缺少金蝶用户Id（UserId），请先绑定金蝶用户")
                    .build();
        }
        if (!properties.isEnabled()) {
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo(no)
                    .submitted(true)
                    .audited(true)
                    .message((useWorkflowAudit ? "模拟提交并工作流审批成功" : "模拟提交并审核成功")
                            + "（kingdee.cloud.enabled=false）: " + no)
                    .build();
        }
        try {
            String session = login();
            String id = billId != null && billId > 0 ? String.valueOf(billId) : "";

            // 已是「审核中 B / 已审核 C」则不再 Submit（金蝶会报：只有暂存/创建/重新审核才允许提交）
            String docStatus = peekDocumentStatus(formId, no, billId);
            boolean skipSubmit = isSubmittedOrAuditedStatus(docStatus);
            if (isAuditedStatus(docStatus)) {
                log.info("Kingdee bill already audited, skip submit+audit formId={} billNo={} status={}",
                        formId, no, docStatus);
                return KingdeeSyncResult.builder()
                        .success(true)
                        .billNo(no)
                        .submitted(true)
                        .audited(true)
                        .message("单据已审核，无需重复提交审核: " + no)
                        .build();
            }

            String submitRequestJson = null;
            String submitResponseJson = null;
            boolean submitted = skipSubmit;
            if (!skipSubmit) {
                submitRequestJson = objectMapper.writeValueAsString(
                        KingdeeBillSubmitPayload.build(objectMapper, no, id));
                submitResponseJson = invokeSubmit(session, formId, no, id);
                submitted = parseOperationSuccess(submitResponseJson);
                if (!submitted) {
                    String submitError = extractOperationError(submitResponseJson);
                    boolean alreadySubmitted = KingdeeSubmitErrorClassifier.isAlreadySubmitted(submitError);
                    if (!alreadySubmitted) {
                        return KingdeeSyncResult.builder()
                                .success(false)
                                .billNo(no)
                                .submitted(false)
                                .audited(false)
                                .message("提交失败: " + submitError)
                                .submitRequestJson(submitRequestJson)
                                .submitResponseJson(submitResponseJson)
                                .build();
                    }
                    submitted = true;
                    log.info("Kingdee bill already submitted, continue {} formId={} billNo={} userId={}",
                            useWorkflowAudit ? "WorkflowAudit" : "Audit", formId, no, kingdeeUserId);
                }
            } else {
                log.info("Kingdee bill status={}, skip Submit and go {} formId={} billNo={}",
                        docStatus, useWorkflowAudit ? "WorkflowAudit" : "Audit", formId, no);
            }

            String auditRequestJson;
            String auditResponseJson;
            if (useWorkflowAudit) {
                ObjectNode workflowData = KingdeeWorkflowAuditPayload.build(
                        objectMapper, formId, no, id,
                        KingdeeWorkflowAuditPayload.APPROVAL_PASS,
                        "WMS PDA 确认提交",
                        kingdeeUserId,
                        kingdeeUserName);
                auditRequestJson = objectMapper.writeValueAsString(workflowData);
                auditResponseJson = invokeWorkflowAudit(session, workflowData);
            } else {
                auditRequestJson = objectMapper.writeValueAsString(
                        KingdeeBillAuditPayload.build(objectMapper, no, id));
                auditResponseJson = invokeAudit(session, formId, no, id);
            }
            if (parseOperationSuccess(auditResponseJson)) {
                return KingdeeSyncResult.builder()
                        .success(true)
                        .billNo(no)
                        .submitted(true)
                        .audited(true)
                        .message((skipSubmit
                                ? (useWorkflowAudit ? "单据已提交，工作流审批成功: " : "单据已提交，审核成功: ")
                                : (useWorkflowAudit ? "提交并工作流审批成功: " : "提交并审核成功: "))
                                + no)
                        .submitRequestJson(submitRequestJson)
                        .submitResponseJson(submitResponseJson)
                        .auditRequestJson(auditRequestJson)
                        .auditResponseJson(auditResponseJson)
                        .build();
            }
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .submitted(submitted)
                    .audited(false)
                    .message((useWorkflowAudit ? "提交成功但工作流审批失败: " : "提交成功但审核失败: ")
                            + extractOperationError(auditResponseJson))
                    .submitRequestJson(submitRequestJson)
                    .submitResponseJson(submitResponseJson)
                    .auditRequestJson(auditRequestJson)
                    .auditResponseJson(auditResponseJson)
                    .build();
        } catch (Exception e) {
            log.error("Kingdee submitAndAudit existing failed formId={} billNo={} workflow={}",
                    formId, no, useWorkflowAudit, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .message(e.getMessage())
                    .build();
        }
    }

    /** View 单据状态；失败时返回 null，由后续 Submit 错误分类兜底 */
    private String peekDocumentStatus(String formId, String billNo, Long billId) {
        try {
            JsonNode node = null;
            if (billId != null && billId > 0) {
                try {
                    node = viewBillById(formId, billId);
                } catch (Exception ex) {
                    log.debug("peekDocumentStatus by Id failed formId={} billId={}", formId, billId);
                }
            }
            if (node == null && StringUtils.hasText(billNo)) {
                node = viewBill(formId, billNo.trim());
            }
            if (node == null) {
                return null;
            }
            return firstText(node, "FDocumentStatus", "DocumentStatus");
        } catch (Exception e) {
            log.warn("peekDocumentStatus failed formId={} billNo={}: {}", formId, billNo, e.getMessage());
            return null;
        }
    }

    private static boolean isSubmittedOrAuditedStatus(String documentStatus) {
        if (!StringUtils.hasText(documentStatus)) {
            return false;
        }
        String s = documentStatus.trim().toUpperCase();
        // B=审核中(已提交) C=已审核 D=重新审核（重新审核通常还需再提交，不跳过）
        return "B".equals(s) || "C".equals(s);
    }

    private static boolean isAuditedStatus(String documentStatus) {
        return StringUtils.hasText(documentStatus) && "C".equalsIgnoreCase(documentStatus.trim());
    }

    /**
     * 直接 Save 创建生产领料单（PRD_PickMtrl），可选自动 Submit + Audit。
     */
    public KingdeeSyncResult syncPickMtrl(KingdeePickMtrlRequest req) {
        String savePayload = KingdeePickMtrlBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getSourceBillNo();
        String formId = properties.getPickMtrlFormId();
        if (!properties.isEnabled()) {
            log.info("Kingdee mock pick-mtrl save for {}", logKey);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo("SCL-MOCK-" + (logKey != null ? logKey : "BATCH"))
                    .submitted(properties.isPickMtrlAutoAudit())
                    .audited(properties.isPickMtrlAutoAudit())
                    .message(properties.isPickMtrlAutoAudit()
                            ? "模拟生产领料保存、提交并审核成功（kingdee.cloud.enabled=false）"
                            : "模拟生产领料同步成功（kingdee.cloud.enabled=false）")
                    .requestJson(savePayload)
                    .responseJson("{\"Result\":{\"ResponseStatus\":{\"IsSuccess\":true},\"Number\":\"SCL-MOCK\"}}")
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId, savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess()) {
                return saveResult;
            }
            if (!properties.isPickMtrlAutoAudit()) {
                return saveResult;
            }
            return submitAndAuditAfterSave(session, formId, saveResult);
        } catch (Exception e) {
            log.error("Kingdee pick-mtrl save failed for {}", logKey, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .responseJson(null)
                    .build();
        }
    }

    /**
     * 直接 Save 创建生产退料单（PRD_ReturnMtrl），可选自动 Submit + Audit。
     */
    public KingdeeSyncResult syncReturnMtrl(KingdeeReturnMtrlRequest req) {
        String savePayload = KingdeeReturnMtrlBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getSourceBillNo();
        String formId = properties.getReturnMtrlFormId();
        if (!properties.isEnabled()) {
            log.info("Kingdee mock return-mtrl save for {}", logKey);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo("SCT-MOCK-" + (logKey != null ? logKey : "BATCH"))
                    .submitted(properties.isReturnMtrlAutoAudit())
                    .audited(properties.isReturnMtrlAutoAudit())
                    .message(properties.isReturnMtrlAutoAudit()
                            ? "模拟生产退料保存、提交并审核成功（kingdee.cloud.enabled=false）"
                            : "模拟生产退料同步成功（kingdee.cloud.enabled=false）")
                    .requestJson(savePayload)
                    .responseJson("{\"Result\":{\"ResponseStatus\":{\"IsSuccess\":true},\"Number\":\"SCT-MOCK\"}}")
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId, savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess()) {
                return saveResult;
            }
            if (!properties.isReturnMtrlAutoAudit()) {
                return saveResult;
            }
            return submitAndAuditAfterSave(session, formId, saveResult);
        } catch (Exception e) {
            log.error("Kingdee return-mtrl save failed for {}", logKey, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .responseJson(null)
                    .build();
        }
    }

    /**
     * 直接 Save 创建委外退料单（SUB_RETURNMTRL），可选自动 Submit + Audit。
     */
    public KingdeeSyncResult syncSubReturnMtrl(KingdeeSubReturnMtrlRequest req) {
        String savePayload = KingdeeSubReturnMtrlBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getSourceBillNo();
        String formId = properties.getSubReturnMtrlFormId();
        if (!properties.isEnabled()) {
            log.info("Kingdee mock sub-return-mtrl save for {}", logKey);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo("WWT-MOCK-" + (logKey != null ? logKey : "BATCH"))
                    .submitted(properties.isSubReturnMtrlAutoAudit())
                    .audited(properties.isSubReturnMtrlAutoAudit())
                    .message(properties.isSubReturnMtrlAutoAudit()
                            ? "模拟委外退料保存、提交并审核成功（kingdee.cloud.enabled=false）"
                            : "模拟委外退料同步成功（kingdee.cloud.enabled=false）")
                    .requestJson(savePayload)
                    .responseJson("{\"Result\":{\"ResponseStatus\":{\"IsSuccess\":true},\"Number\":\"WWT-MOCK\"}}")
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId, savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess()) {
                return saveResult;
            }
            if (!properties.isSubReturnMtrlAutoAudit()) {
                return saveResult;
            }
            return submitAndAuditAfterSave(session, formId, saveResult);
        } catch (Exception e) {
            log.error("Kingdee sub-return-mtrl save failed for {}", logKey, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .responseJson(null)
                    .build();
        }
    }

    /**
     * 直接 Save 创建委外领料单（SUB_PickMtrl），可选自动 Submit + Audit。
     */
    public KingdeeSyncResult syncSubPickMtrl(KingdeeSubPickMtrlRequest req) {
        String savePayload = KingdeeSubPickMtrlBuilder.build(objectMapper, properties, req);
        String logKey = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : req.getSourceBillNo();
        String formId = properties.getSubPickMtrlFormId();
        if (!properties.isEnabled()) {
            log.info("Kingdee mock sub-pick-mtrl save for {}", logKey);
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo("WWL-MOCK-" + (logKey != null ? logKey : "BATCH"))
                    .submitted(properties.isSubPickMtrlAutoAudit())
                    .audited(properties.isSubPickMtrlAutoAudit())
                    .message(properties.isSubPickMtrlAutoAudit()
                            ? "模拟委外领料保存、提交并审核成功（kingdee.cloud.enabled=false）"
                            : "模拟委外领料同步成功（kingdee.cloud.enabled=false）")
                    .requestJson(savePayload)
                    .responseJson("{\"Result\":{\"ResponseStatus\":{\"IsSuccess\":true},\"Number\":\"WWL-MOCK\"}}")
                    .build();
        }
        try {
            String session = login();
            String saveResponseJson = invokeSave(session, formId, savePayload);
            KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
            if (!saveResult.isSuccess()) {
                return saveResult;
            }
            if (!properties.isSubPickMtrlAutoAudit()) {
                return saveResult;
            }
            return submitAndAuditAfterSave(session, formId, saveResult);
        } catch (Exception e) {
            log.error("Kingdee sub-pick-mtrl save failed for {}", logKey, e);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .requestJson(savePayload)
                    .responseJson(null)
                    .build();
        }
    }

    private String login() throws Exception {
        long now = System.currentTimeMillis();
        String cached = cachedSessionCookie;
        if (StringUtils.hasText(cached) && now < sessionExpireAtMs) {
            return cached;
        }
        synchronized (sessionLock) {
            now = System.currentTimeMillis();
            if (StringUtils.hasText(cachedSessionCookie) && now < sessionExpireAtMs) {
                return cachedSessionCookie;
            }
            ObjectNode body = objectMapper.createObjectNode();
            body.put("acctid", properties.getAcctId());
            body.put("username", properties.getUsername());
            body.put("password", properties.getPassword());
            body.put("lcid", 2052);
            String url = properties.getBaseUrl() + "/Kingdee.BOS.WebApi.ServicesStub.AuthService.ValidateUser.common.kdsvc";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> resp = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body.toString(), headers), String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new IllegalStateException("金蝶登录失败: " + resp.getStatusCode());
            }
            JsonNode node = objectMapper.readTree(resp.getBody());
            if (node.path("LoginResultType").asInt() != 1) {
                String msg = node.path("Message").asText("金蝶登录失败");
                throw new IllegalStateException("金蝶登录失败: " + msg
                        + "（请检查 base-url、acct-id、用户名密码）");
            }
            String cookie = resp.getHeaders().getFirst("Set-Cookie");
            if (!StringUtils.hasText(cookie)) {
                throw new IllegalStateException("金蝶登录成功但未返回会话 Cookie");
            }
            cachedSessionCookie = cookie;
            // 金蝶会话通常较长，本地缓存 25 分钟，超时后自动重新登录
            sessionExpireAtMs = System.currentTimeMillis() + 25L * 60L * 1000L;
            log.debug("Kingdee session refreshed, ttl=25m");
            return cookie;
        }
    }

    /** 会话失效时强制下次重新登录 */
    public void invalidateSession() {
        synchronized (sessionLock) {
            cachedSessionCookie = null;
            sessionExpireAtMs = 0L;
        }
    }

    private String invokeSave(String sessionCookie, String formId, String payload) throws Exception {
        return postWithSession(
                "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Save.common.kdsvc",
                sessionCookie,
                root -> {
                    root.put("formid", formId);
                    root.put("data", payload);
                });
    }

    private String invokePush(String sessionCookie, String formId, String payload) throws Exception {
        return postWithSession(
                "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Push.common.kdsvc",
                sessionCookie,
                root -> {
                    root.put("formid", formId);
                    root.put("data", payload);
                });
    }

    private String invokeSubmit(String sessionCookie, String formId, String billNo, String billId) throws Exception {
        ObjectNode submitData = KingdeeBillSubmitPayload.build(objectMapper, billNo, billId);
        return postWithSession(
                "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Submit.common.kdsvc",
                sessionCookie,
                root -> {
                    root.put("formid", formId);
                    root.set("data", submitData);
                });
    }

    private String invokeAudit(String sessionCookie, String formId, String billNo, String billId) throws Exception {
        ObjectNode auditData = KingdeeBillAuditPayload.build(objectMapper, billNo, billId);
        return postWithSession(
                "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.Audit.common.kdsvc",
                sessionCookie,
                root -> {
                    root.put("formid", formId);
                    root.set("data", auditData);
                });
    }

    /**
     * 工作流审批：data 内含 FormId / Numbers / Approval / ApprovalType 等。
     */
    private String invokeWorkflowAudit(String sessionCookie, ObjectNode workflowData) throws Exception {
        return postWithSession(
                "Kingdee.BOS.WebApi.ServicesStub.DynamicFormService.WorkflowAudit.common.kdsvc",
                sessionCookie,
                root -> root.put("data", objectMapper.writeValueAsString(workflowData)));
    }

    private KingdeeSyncResult submitAndAuditAfterSave(String session, String formId, KingdeeSyncResult saveResult) throws Exception {
        String billNo = saveResult.getBillNo();
        if (!StringUtils.hasText(billNo)) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(null)
                    .submitted(false)
                    .audited(false)
                    .message("保存成功但未返回单据编号，无法提交")
                    .requestJson(saveResult.getRequestJson())
                    .responseJson(saveResult.getResponseJson())
                    .build();
        }
        String billId = extractBillId(saveResult.getResponseJson());
        String submitRequestJson = objectMapper.writeValueAsString(
                KingdeeBillSubmitPayload.build(objectMapper, billNo, billId));
        String submitResponseJson = invokeSubmit(session, formId, billNo, billId);
        if (!parseOperationSuccess(submitResponseJson)) {
            String submitError = extractOperationError(submitResponseJson);
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(billNo)
                    .submitted(false)
                    .audited(false)
                    .message("保存成功但提交失败: " + submitError)
                    .requestJson(saveResult.getRequestJson())
                    .responseJson(saveResult.getResponseJson())
                    .submitRequestJson(submitRequestJson)
                    .submitResponseJson(submitResponseJson)
                    .build();
        }

        String auditRequestJson = objectMapper.writeValueAsString(
                KingdeeBillAuditPayload.build(objectMapper, billNo, billId));
        String auditResponseJson = invokeAudit(session, formId, billNo, billId);
        boolean auditSuccess = parseOperationSuccess(auditResponseJson);
        if (auditSuccess) {
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo(billNo)
                    .submitted(true)
                    .audited(true)
                    .message("保存、提交并审核成功: " + billNo)
                    .requestJson(saveResult.getRequestJson())
                    .responseJson(saveResult.getResponseJson())
                    .submitRequestJson(submitRequestJson)
                    .submitResponseJson(submitResponseJson)
                    .auditRequestJson(auditRequestJson)
                    .auditResponseJson(auditResponseJson)
                    .build();
        }
        String auditError = extractOperationError(auditResponseJson);
        return KingdeeSyncResult.builder()
                .success(false)
                .billNo(billNo)
                .submitted(true)
                .audited(false)
                .message("保存并提交成功，但审核失败: " + auditError)
                .requestJson(saveResult.getRequestJson())
                .responseJson(saveResult.getResponseJson())
                .submitRequestJson(submitRequestJson)
                .submitResponseJson(submitResponseJson)
                .auditRequestJson(auditRequestJson)
                .auditResponseJson(auditResponseJson)
                .build();
    }

    @FunctionalInterface
    private interface BodyBuilder {
        void build(ObjectNode root) throws Exception;
    }

    private String postWithSession(String servicePath, String sessionCookie, BodyBuilder builder) throws Exception {
        String url = properties.getBaseUrl() + "/" + servicePath;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(sessionCookie)) {
            headers.set(HttpHeaders.COOKIE, sessionCookie);
        }
        ObjectNode root = objectMapper.createObjectNode();
        builder.build(root);
        ResponseEntity<String> resp = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(root.toString(), headers), String.class);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("金蝶接口调用失败: " + resp.getStatusCode());
        }
        return resp.getBody();
    }

    private boolean isSessionExpiredResponse(String responseJson) {
        if (!StringUtils.hasText(responseJson)) {
            return false;
        }
        String lower = responseJson.toLowerCase();
        return lower.contains("会话信息已丢失")
                || lower.contains("会话已过期")
                || lower.contains("contextlost")
                || lower.contains("\"msgcode\":\"1\"")
                || lower.contains("\"msgcode\":1");
    }

    private List<List<String>> parseBillQueryRows(String responseJson) throws Exception {
        if (!StringUtils.hasText(responseJson)) {
            return List.of();
        }
        String trimmed = responseJson.trim();
        if (trimmed.startsWith("response_error:")) {
            String msg = trimmed.substring("response_error:".length()).trim();
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "金蝶接口返回错误: " + msg, "KINGDEE_API_ERROR");
        }
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode rowsNode = extractQueryRowsNode(root);
        if (rowsNode == null || !rowsNode.isArray() || rowsNode.isEmpty()) {
            log.warn("Kingdee ExecuteBillQuery empty or unrecognized response: {}",
                    responseJson.length() > 500 ? responseJson.substring(0, 500) + "..." : responseJson);
            return List.of();
        }
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode rowNode : rowsNode) {
            List<String> row = parseQueryRow(rowNode);
            if (!row.isEmpty()) {
                rows.add(row);
            }
        }
        return rows;
    }

    private JsonNode extractQueryRowsNode(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isArray()) {
            return root;
        }
        if (root.isObject() && root.has("Result")) {
            JsonNode result = root.get("Result");
            if (result != null && result.isArray()) {
                return result;
            }
            if (result != null && result.isObject()) {
                JsonNode status = result.path("ResponseStatus");
                if (status.isObject()) {
                    JsonNode isSuccess = status.get("IsSuccess");
                    boolean success = isSuccess == null
                            || isSuccess.asBoolean(true)
                            || "true".equalsIgnoreCase(isSuccess.asText());
                    if (!success) {
                        throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                                "金蝶查询失败: " + status.path("Errors").toString());
                    }
                }
                JsonNode inner = result.get("Result");
                if (inner != null && inner.isArray()) {
                    return inner;
                }
            }
        }
        return null;
    }

    private List<String> parseQueryRow(JsonNode rowNode) {
        if (rowNode == null || rowNode.isNull()) {
            return List.of();
        }
        if (rowNode.isArray()) {
            List<String> row = new ArrayList<>();
            rowNode.forEach(cell -> row.add(cell.isNull() ? "" : cell.asText("").trim()));
            return row;
        }
        if (rowNode.isTextual()) {
            String text = rowNode.asText("").trim();
            if (!StringUtils.hasText(text)) {
                return List.of();
            }
            if (text.contains("\t")) {
                return List.of(text.split("\t", -1));
            }
            return List.of(text.split(",", -1));
        }
        return List.of();
    }

    private KingdeeSyncResult parseSaveResponse(String requestJson, String responseJson) throws Exception {
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode result = root.path("Result");
        JsonNode status = result.path("ResponseStatus");
        boolean success = isKingdeeSuccess(status.path("IsSuccess"));
        String billNo = result.path("Number").asText("");
        if (!StringUtils.hasText(billNo)) {
            JsonNode needReturn = result.path("NeedReturnData");
            if (needReturn.isArray() && !needReturn.isEmpty()) {
                billNo = needReturn.get(0).path("FBillNo").asText("");
                if (!StringUtils.hasText(billNo)) {
                    billNo = needReturn.get(0).path("BillNo").asText("");
                }
            }
        }
        if (!StringUtils.hasText(billNo)) {
            JsonNode successEntities = status.path("SuccessEntitys");
            if (successEntities.isArray() && !successEntities.isEmpty()) {
                billNo = successEntities.get(0).path("Number").asText("");
            }
        }
        if (success && !StringUtils.hasText(billNo)) {
            billNo = "KD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        String message = success ? "保存成功" : extractOperationError(responseJson);
        return KingdeeSyncResult.builder()
                .success(success)
                .billNo(success ? billNo : null)
                .audited(false)
                .message(message)
                .requestJson(requestJson)
                .responseJson(responseJson)
                .build();
    }

    private String extractBillId(String responseJson) throws Exception {
        if (!StringUtils.hasText(responseJson)) {
            return "";
        }
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode result = root.path("Result");
        String billId = result.path("Id").asText("");
        if (StringUtils.hasText(billId)) {
            return billId;
        }
        JsonNode needReturn = result.path("NeedReturnData");
        if (needReturn.isArray() && !needReturn.isEmpty()) {
            billId = needReturn.get(0).path("FID").asText("");
            if (StringUtils.hasText(billId)) {
                return billId;
            }
            billId = needReturn.get(0).path("Id").asText("");
            if (StringUtils.hasText(billId)) {
                return billId;
            }
        }
        JsonNode successEntities = result.path("ResponseStatus").path("SuccessEntitys");
        if (successEntities.isArray() && !successEntities.isEmpty()) {
            billId = successEntities.get(0).path("Id").asText("");
            if (StringUtils.hasText(billId)) {
                return billId;
            }
        }
        return "";
    }

    private boolean parseOperationSuccess(String responseJson) throws Exception {
        if (!StringUtils.hasText(responseJson)) {
            return false;
        }
        String trimmed = responseJson.trim();
        if (trimmed.startsWith("response_error:")) {
            return false;
        }
        JsonNode root = objectMapper.readTree(responseJson);
        JsonNode status = root.path("Result").path("ResponseStatus");
        if (status.isObject()) {
            JsonNode isSuccess = status.get("IsSuccess");
            if (isSuccess != null && !isSuccess.isNull() && !isSuccess.isMissingNode()) {
                return isKingdeeSuccess(isSuccess);
            }
            JsonNode errors = status.get("Errors");
            return errors == null || errors.isNull()
                    || (errors.isArray() && errors.isEmpty());
        }
        // WorkflowAudit 文档示例可能返回 ResponseStatus="" ，无明确失败则视为成功
        if (status.isTextual() || status.isMissingNode() || status.isNull()) {
            String lower = trimmed.toLowerCase();
            if (lower.contains("\"issuccess\":false")) {
                return false;
            }
            return root.path("Result").isObject() || root.path("Result").isTextual();
        }
        return false;
    }

    private boolean isKingdeeSuccess(JsonNode isSuccess) {
        if (isSuccess == null || isSuccess.isMissingNode() || isSuccess.isNull()) {
            return false;
        }
        return isSuccess.asBoolean(false) || "true".equalsIgnoreCase(isSuccess.asText());
    }

    private String extractOperationError(String responseJson) {
        try {
            if (!StringUtils.hasText(responseJson)) {
                return "金蝶接口无响应";
            }
            String trimmed = responseJson.trim();
            if (trimmed.startsWith("response_error:")) {
                return trimmed.substring("response_error:".length()).trim();
            }
            JsonNode status = objectMapper.readTree(responseJson).path("Result").path("ResponseStatus");
            String err = status.path("Errors").toString();
            if (StringUtils.hasText(err) && !"null".equals(err)) {
                return KingdeeResponseMessageFormatter.format(err);
            }
            String msg = status.path("Message").asText("");
            return StringUtils.hasText(msg) ? msg : status.toString();
        } catch (Exception ex) {
            return responseJson;
        }
    }
}
