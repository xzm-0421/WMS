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
            data.put("FilterString", filterString == null ? "" : filterString);
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
        return isKingdeeSuccess(root.path("Result").path("ResponseStatus").path("IsSuccess"));
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
