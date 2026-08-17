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
            String pushRequestJson = objectMapper.writeValueAsString(pushData);
            String pushResponseJson = invokePush(session, srcFormId, pushRequestJson);
            KingdeeSyncResult pushResult = parseSaveResponse(pushRequestJson, pushResponseJson);
            String outStockId = extractBillId(pushResponseJson);
            Long outStockIdLong = parseLongQuiet(outStockId);
            String outStockNo = pushResult.getBillNo();
            if (!StringUtils.hasText(outStockNo) && outStockIdLong != null) {
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
                    // 下推规则已带出批号/仓库时，分录匹配失败可跳过补全，直接 Submit+Audit
                    log.warn("Skip sal-outstock lot/stock fill after push, proceed to submit+audit. deliveryBillNo={} outStockNo={} reason={}",
                            no, outStockNo, fillResult.getMessage());
                }
            }

            if (!StringUtils.hasText(outStockNo) && outStockIdLong != null) {
                outStockNo = resolveBillNoByView(targetFormId, outStockIdLong);
            }
            if (!StringUtils.hasText(outStockNo)) {
                return KingdeeSyncResult.builder()
                        .success(false)
                        .billNo(no)
                        .message("销售出库单号为空，无法提交审核")
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
            billNode = viewBillById(formId, billId);
        }
        if (billNode == null && StringUtils.hasText(billNo)) {
            billNode = viewBill(formId, billNo.trim());
        }
        if (billNode == null) {
            return KingdeeSyncResult.builder()
                    .success(false)
                    .message("无法 View 销售出库单以补全批号/仓库")
                    .build();
        }
        String resolvedNo = firstText(billNode, "FBillNo", "BillNo", "Number");
        Long resolvedId = parseLongQuiet(firstText(billNode, "FID", "Id"));
        if (resolvedId == null) {
            resolvedId = billId;
        }
        List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> updates =
                matchOutStockEntriesForFill(billNode, fills);
        if (updates.isEmpty()) {
            if (outStockEntriesHaveLotAndStock(billNode)) {
                log.info("Sal-outstock already has lot/stock after push, skip fill. billNo={}", resolvedNo);
                return KingdeeSyncResult.builder()
                        .success(true)
                        .billNo(resolvedNo)
                        .message("下推已带出批号/仓库，跳过补全")
                        .build();
            }
            // 匹配失败：交由上层决定是否仍 Submit+Audit（多数账套下推已带齐字段）
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(resolvedNo)
                    .message("销售出库分录与 PDA 扫码行未能匹配，跳过补全")
                    .build();
        }
        String savePayload = KingdeeSalOutStockLotStockBuilder.build(
                objectMapper, resolvedId, resolvedNo, updates);
        String saveResponseJson = invokeSave(session, formId, savePayload);
        KingdeeSyncResult saveResult = parseSaveResponse(savePayload, saveResponseJson);
        if (saveResult.isSuccess() && !StringUtils.hasText(saveResult.getBillNo()) && StringUtils.hasText(resolvedNo)) {
            return KingdeeSyncResult.builder()
                    .success(true)
                    .billNo(resolvedNo)
                    .requestJson(savePayload)
                    .responseJson(saveResponseJson)
                    .build();
        }
        return saveResult;
    }

    private List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> matchOutStockEntriesForFill(
            JsonNode billNode, List<KingdeeSalOutStockEntryFill> fills) {
        JsonNode entries = billNode.get("FEntity");
        if (entries == null || !entries.isArray()) {
            entries = billNode.get("Entity");
        }
        List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> updates = new ArrayList<>();
        if (entries == null || !entries.isArray() || fills == null) {
            return updates;
        }
        List<KingdeeSalOutStockEntryFill> pending = new ArrayList<>(fills);
        for (JsonNode entry : entries) {
            if (entry == null || entry.isNull()) {
                continue;
            }
            Long entryId = parseLongQuiet(firstText(entry, "FENTRYID", "FEntryID", "EntryID", "Id"));
            if (entryId == null || entryId <= 0) {
                continue;
            }
            String material = refNumber(entry, "FMaterialID", "FMaterialId", "MaterialID");
            Long srcEntryId = extractLinkSourceEntryId(entry);
            KingdeeSalOutStockEntryFill matched = null;
            for (int i = 0; i < pending.size(); i++) {
                KingdeeSalOutStockEntryFill fill = pending.get(i);
                if (fill == null) {
                    continue;
                }
                boolean bySrc = fill.getSourceEntryId() != null && fill.getSourceEntryId() > 0
                        && srcEntryId != null && fill.getSourceEntryId().equals(srcEntryId);
                boolean byMaterial = StringUtils.hasText(fill.getMaterialCode())
                        && StringUtils.hasText(material)
                        && fill.getMaterialCode().trim().equalsIgnoreCase(material.trim());
                if (bySrc || byMaterial) {
                    matched = fill;
                    pending.remove(i);
                    break;
                }
            }
            if (matched == null) {
                continue;
            }
            updates.add(new KingdeeSalOutStockLotStockBuilder.EntryUpdate(
                    entryId, matched.getLotNumber(), matched.getStockNumber(), matched.getRealQty()));
        }
        // 剩余按物料再匹配一次（同分录多行时）
        if (!pending.isEmpty()) {
            for (JsonNode entry : entries) {
                if (pending.isEmpty()) {
                    break;
                }
                Long entryId = parseLongQuiet(firstText(entry, "FENTRYID", "FEntryID", "EntryID", "Id"));
                if (entryId == null || entryId <= 0) {
                    continue;
                }
                boolean already = updates.stream().anyMatch(u -> entryId.equals(u.entryId()));
                if (already) {
                    continue;
                }
                String material = refNumber(entry, "FMaterialID", "FMaterialId", "MaterialID");
                for (int i = 0; i < pending.size(); i++) {
                    KingdeeSalOutStockEntryFill fill = pending.get(i);
                    if (fill != null && StringUtils.hasText(fill.getMaterialCode())
                            && StringUtils.hasText(material)
                            && fill.getMaterialCode().trim().equalsIgnoreCase(material.trim())) {
                        updates.add(new KingdeeSalOutStockLotStockBuilder.EntryUpdate(
                                entryId, fill.getLotNumber(), fill.getStockNumber(), fill.getRealQty()));
                        pending.remove(i);
                        break;
                    }
                }
            }
        }
        return updates;
    }

    private static boolean outStockEntriesHaveLotAndStock(JsonNode billNode) {
        JsonNode entries = billNode != null ? billNode.get("FEntity") : null;
        if (entries == null || !entries.isArray()) {
            entries = billNode != null ? billNode.get("Entity") : null;
        }
        if (entries == null || !entries.isArray() || entries.isEmpty()) {
            return false;
        }
        for (JsonNode entry : entries) {
            if (entry == null || entry.isNull()) {
                continue;
            }
            String lot = firstNonBlank(
                    firstText(entry, "FLot_Text", "Lot_Text"),
                    refNumber(entry, "FLot", "Lot"));
            String stock = refNumber(entry, "FStockID", "FStockId", "StockID", "StockId");
            if (!StringUtils.hasText(lot) || !StringUtils.hasText(stock)) {
                return false;
            }
        }
        return true;
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

    private static Long extractLinkSourceEntryId(JsonNode entry) {
        JsonNode links = entry.get("FEntity_Link");
        if (links == null || !links.isArray() || links.isEmpty()) {
            return null;
        }
        JsonNode link = links.get(0);
        return parseLongQuiet(firstText(link,
                "FEntity_Link_FSId", "FSId", "FEntity_Link_FSID", "SourceEntryId"));
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
     * 若单据已是审核中(B)，Submit 失败时继续 Audit。
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
            String submitRequestJson = objectMapper.writeValueAsString(
                    KingdeeBillSubmitPayload.build(objectMapper, no, id));
            String submitResponseJson = invokeSubmit(session, formId, no, id);
            boolean submitted = parseOperationSuccess(submitResponseJson);
            if (!submitted) {
                String submitError = extractOperationError(submitResponseJson);
                // 已提交过的单据直接走审批
                boolean alreadySubmitted = submitError != null
                        && (submitError.contains("提交") || submitError.contains("审核中")
                        || submitError.contains("已提交") || submitError.contains("工作流")
                        || submitError.toLowerCase().contains("submit"));
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
                log.info("Kingdee bill already submitted, continue {} formId={} billNo={} userId={}",
                        useWorkflowAudit ? "WorkflowAudit" : "Audit", formId, no, kingdeeUserId);
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
                        .message((useWorkflowAudit ? "提交并工作流审批成功: " : "提交并审核成功: ") + no)
                        .submitRequestJson(submitRequestJson)
                        .submitResponseJson(submitResponseJson)
                        .auditRequestJson(auditRequestJson)
                        .auditResponseJson(auditResponseJson)
                        .build();
            }
            return KingdeeSyncResult.builder()
                    .success(false)
                    .billNo(no)
                    .submitted(true)
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
