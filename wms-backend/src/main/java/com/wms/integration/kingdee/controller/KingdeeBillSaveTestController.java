package com.wms.integration.kingdee.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.common.result.ApiResult;
import com.wms.integration.kingdee.KingdeeCloudProperties;
import com.wms.integration.kingdee.KingdeeCloudService;
import com.wms.integration.kingdee.KingdeeMiscInStockBuilder;
import com.wms.integration.kingdee.KingdeeMiscInStockRequest;
import com.wms.integration.kingdee.KingdeeMisDeliveryBuilder;
import com.wms.integration.kingdee.KingdeeMisDeliveryRequest;
import com.wms.integration.kingdee.KingdeePickMtrlBuilder;
import com.wms.integration.kingdee.KingdeePickMtrlRequest;
import com.wms.integration.kingdee.KingdeePrdInStockBuilder;
import com.wms.integration.kingdee.KingdeePrdInStockRequest;
import com.wms.integration.kingdee.KingdeeReturnMtrlBuilder;
import com.wms.integration.kingdee.KingdeeReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubPickMtrlBuilder;
import com.wms.integration.kingdee.KingdeeSubPickMtrlRequest;
import com.wms.integration.kingdee.KingdeeSubReturnMtrlBuilder;
import com.wms.integration.kingdee.KingdeeSubReturnMtrlRequest;
import com.wms.integration.kingdee.KingdeeSyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 金蝶单据 Save 联调入口：可预览 Builder 报文，或直接提交完整 Save JSON。
 */
@Tag(name = "金蝶-单据Save联调")
@RestController
@RequestMapping("/integration/kingdee/test")
@RequiredArgsConstructor
public class KingdeeBillSaveTestController {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;
    private final ObjectMapper objectMapper;

    @Operation(summary = "七类单据 FormId / 单据类型一览")
    @GetMapping("/catalog")
    public ApiResult<Map<String, Object>> catalog() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("PRD_INSTOCK", Map.of("name", "生产入库", "formId", properties.getPrdInStockFormId(),
                "billType", properties.getPrdInStockBillTypeNumber()));
        map.put("PRD_ReturnMtrl", Map.of("name", "生产退料", "formId", properties.getReturnMtrlFormId(),
                "billType", properties.getReturnMtrlBillTypeNumber()));
        map.put("SUB_RETURNMTRL", Map.of("name", "委外退料", "formId", properties.getSubReturnMtrlFormId(),
                "billType", properties.getSubReturnMtrlBillTypeNumber()));
        map.put("STK_Miscellaneous", Map.of("name", "其他入库", "formId", properties.getMiscInStockFormId(),
                "billType", properties.getMiscInStockBillTypeNumber()));
        map.put("PRD_PickMtrl", Map.of("name", "生产领料", "formId", properties.getPickMtrlFormId(),
                "billType", properties.getPickMtrlBillTypeNumber()));
        map.put("SUB_PickMtrl", Map.of("name", "委外领料", "formId", properties.getSubPickMtrlFormId(),
                "billType", properties.getSubPickMtrlBillTypeNumber()));
        map.put("STK_MisDelivery", Map.of("name", "其他出库", "formId", properties.getMisDeliveryFormId(),
                "billType", properties.getMisDeliveryBillTypeNumber()));
        map.put("kingdeeEnabled", kingdeeCloudService.isEnabled());
        return ApiResult.ok(map);
    }

    @Operation(summary = "预览 Builder 生成的 Save 报文（不调金蝶）")
    @GetMapping("/preview/{billKind}")
    public ApiResult<Map<String, Object>> preview(@PathVariable String billKind) throws Exception {
        String kind = billKind == null ? "" : billKind.trim().toUpperCase();
        String formId;
        String payload;
        switch (kind) {
            case "PRD_INSTOCK", "PRODUCTION_IN" -> {
                formId = properties.getPrdInStockFormId();
                payload = KingdeePrdInStockBuilder.build(objectMapper, properties, samplePrdInStock());
            }
            case "PRD_RETURNMTRL", "PRODUCTION_RETURN" -> {
                formId = properties.getReturnMtrlFormId();
                payload = KingdeeReturnMtrlBuilder.build(objectMapper, properties, sampleReturnMtrl());
            }
            case "SUB_RETURNMTRL", "OUTSOURCE_RETURN" -> {
                formId = properties.getSubReturnMtrlFormId();
                payload = KingdeeSubReturnMtrlBuilder.build(objectMapper, properties, sampleSubReturn());
            }
            case "STK_MISCELLANEOUS", "OTHER_IN" -> {
                formId = properties.getMiscInStockFormId();
                payload = KingdeeMiscInStockBuilder.build(objectMapper, properties, sampleMiscIn());
            }
            case "PRD_PICKMTRL", "PRODUCTION_ISSUE" -> {
                formId = properties.getPickMtrlFormId();
                payload = KingdeePickMtrlBuilder.build(objectMapper, properties, samplePickMtrl());
            }
            case "SUB_PICKMTRL", "OUTSOURCE_ISSUE" -> {
                formId = properties.getSubPickMtrlFormId();
                payload = KingdeeSubPickMtrlBuilder.build(objectMapper, properties, sampleSubPick());
            }
            case "STK_MISDELIVERY", "OTHER_OUT" -> {
                formId = properties.getMisDeliveryFormId();
                payload = KingdeeMisDeliveryBuilder.build(objectMapper, properties, sampleMisDelivery());
            }
            default -> {
                return ApiResult.fail(400, "未知 billKind，可用：PRD_INSTOCK / PRD_ReturnMtrl / SUB_RETURNMTRL / "
                        + "STK_Miscellaneous / PRD_PickMtrl / SUB_PickMtrl / STK_MisDelivery");
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("formId", formId);
        data.put("payload", objectMapper.readTree(payload));
        return ApiResult.ok(data);
    }

    @Operation(summary = "直接提交完整 Save JSON 到金蝶（联调）")
    @PostMapping("/save")
    public ApiResult<KingdeeSyncResult> rawSave(@RequestBody RawSaveRequest request,
                                                @RequestParam(defaultValue = "false") boolean autoAudit) {
        if (request == null || !StringUtils.hasText(request.getFormId())) {
            return ApiResult.fail(400, "formId 不能为空");
        }
        String dataJson;
        try {
            if (request.getData() != null) {
                dataJson = objectMapper.writeValueAsString(request.getData());
            } else if (StringUtils.hasText(request.getDataJson())) {
                dataJson = request.getDataJson();
            } else {
                return ApiResult.fail(400, "请传 data（对象）或 dataJson（字符串）");
            }
        } catch (Exception e) {
            return ApiResult.fail(400, "Save 报文序列化失败: " + e.getMessage());
        }
        boolean audit = request.getAutoAudit() != null ? request.getAutoAudit() : autoAudit;
        KingdeeSyncResult result = kingdeeCloudService.rawSave(request.getFormId().trim(), dataJson, audit);
        return result.isSuccess() ? ApiResult.ok(result.getMessage(), result) : ApiResult.ok(result.getMessage(), result);
    }

    @Operation(summary = "用样例数据调用 Builder 并提交金蝶（联调）")
    @PostMapping("/save-sample/{billKind}")
    public ApiResult<KingdeeSyncResult> saveSample(@PathVariable String billKind,
                                                   @RequestParam(defaultValue = "false") boolean autoAudit)
            throws Exception {
        ApiResult<Map<String, Object>> preview = preview(billKind);
        if (preview.getCode() != 200 || preview.getData() == null) {
            return ApiResult.fail(400, preview.getMessage() != null ? preview.getMessage() : "预览失败");
        }
        String formId = String.valueOf(preview.getData().get("formId"));
        Object payloadObj = preview.getData().get("payload");
        String payloadJson = payloadObj instanceof JsonNode
                ? objectMapper.writeValueAsString(payloadObj)
                : String.valueOf(payloadObj);
        KingdeeSyncResult result = kingdeeCloudService.rawSave(formId, payloadJson, autoAudit);
        return ApiResult.ok(result.getMessage(), result);
    }

    @Data
    public static class RawSaveRequest {
        private String formId;
        /** Save 根对象：NeedUpDateFields / Model ... */
        private JsonNode data;
        /** 兼容字符串报文 */
        private String dataJson;
        private Boolean autoAudit;
    }

    private KingdeePrdInStockRequest samplePrdInStock() {
        return KingdeePrdInStockRequest.builder()
                .batchNo("TEST-SCRK")
                .billDate(LocalDate.now())
                .lines(List.of(KingdeePrdInStockRequest.Line.builder()
                        .materialCode("F114-WS0033.G-0001N-101")
                        .unitCode("Pcs")
                        .mustQty(new BigDecimal("10"))
                        .realQty(new BigDecimal("10"))
                        .workShopCode(properties.getPrdInStockWorkShopNumber())
                        .moBillNo("MO26050141")
                        .moId(135931L)
                        .moEntryId(166413L)
                        .moEntrySeq(1)
                        .srcEntryId(166413L)
                        .srcInterId(135931L)
                        .srcBillNo("MO26050141")
                        .inStockType("1")
                        .productType("1")
                        .backFlush(true)
                        .build()))
                .build();
    }

    private KingdeePickMtrlRequest samplePickMtrl() {
        return KingdeePickMtrlRequest.builder()
                .batchNo("TEST-SCLL")
                .sourceBillNo("PPBOM260600286")
                .sourceBillId(168462L)
                .workShopCode(properties.getPickMtrlWorkShopNumber())
                .billDate(LocalDate.now())
                .lines(List.of(KingdeePickMtrlRequest.Line.builder()
                        .materialCode("GB0020")
                        .unitCode("kg")
                        .warehouseCode("CK011")
                        .batchNo("20240625")
                        .quantity(new BigDecimal("2.77984"))
                        .parentMaterialCode("F126GS-075160-0001BN-01")
                        .moBillNo("MO26060113")
                        .moId(136715L)
                        .moEntryId(168997L)
                        .moEntrySeq(18)
                        .ppBomBillNo("PPBOM260600286")
                        .ppBomBillId(168462L)
                        .ppBomEntryId(288055L)
                        .sourceLineNo(1)
                        .build()))
                .build();
    }

    private KingdeeReturnMtrlRequest sampleReturnMtrl() {
        return KingdeeReturnMtrlRequest.builder()
                .batchNo("TEST-SCTL")
                .sourceBillNo("SOUT260601020")
                .sourceBillId(201392L)
                .workShopCode(properties.getReturnMtrlWorkShopNumber())
                .billDate(LocalDate.now())
                .lines(List.of(KingdeeReturnMtrlRequest.Line.builder()
                        .materialCode("TGS-052160-0001GG-01")
                        .unitCode("tao")
                        .warehouseCode("CK027")
                        .batchNo("20260609")
                        .quantity(new BigDecimal("2427"))
                        .parentMaterialCode("TDA0237.L1-000101")
                        .moBillNo("MO26030328")
                        .moId(134351L)
                        .moEntryId(162305L)
                        .moEntrySeq(1)
                        .ppBomBillNo("PPBOM260301027")
                        .ppBomEntryId(265742L)
                        .sourceLineNo(1)
                        .pickBillId(201392L)
                        .pickEntryId(381310L)
                        .build()))
                .build();
    }

    private KingdeeSubPickMtrlRequest sampleSubPick() {
        return KingdeeSubPickMtrlRequest.builder()
                .batchNo("TEST-WWLL")
                .sourceBillNo("SUBPPBOM-TEST")
                .billDate(LocalDate.now())
                .supplierCode(properties.getSubPickMtrlDefaultSupplierNumber())
                .lines(List.of(KingdeeSubPickMtrlRequest.Line.builder()
                        .materialCode("MAT-TEST-001")
                        .unitCode(properties.getStockInDefaultUnitNumber())
                        .warehouseCode(properties.getStockInDefaultWarehouseNumber())
                        .quantity(BigDecimal.ONE)
                        .build()))
                .build();
    }

    private KingdeeSubReturnMtrlRequest sampleSubReturn() {
        return KingdeeSubReturnMtrlRequest.builder()
                .batchNo("TEST-WWTL")
                .sourceBillNo("SUBPICK-TEST")
                .billDate(LocalDate.now())
                .supplierCode(properties.getSubReturnMtrlDefaultSupplierNumber())
                .lines(List.of(KingdeeSubReturnMtrlRequest.Line.builder()
                        .materialCode("MAT-TEST-001")
                        .unitCode(properties.getStockInDefaultUnitNumber())
                        .warehouseCode(properties.getStockInDefaultWarehouseNumber())
                        .quantity(BigDecimal.ONE)
                        .build()))
                .build();
    }

    private KingdeeMiscInStockRequest sampleMiscIn() {
        return KingdeeMiscInStockRequest.builder()
                .batchNo("TEST-QTRK")
                .billDate(LocalDate.now())
                .deptCode(properties.getMiscInStockDeptNumber())
                .lines(List.of(KingdeeMiscInStockRequest.Line.builder()
                        .materialCode("MAT-TEST-001")
                        .unitCode(properties.getStockInDefaultUnitNumber())
                        .warehouseCode(properties.getStockInDefaultWarehouseNumber())
                        .quantity(BigDecimal.ONE)
                        .inStockType("1")
                        .build()))
                .build();
    }

    private KingdeeMisDeliveryRequest sampleMisDelivery() {
        return KingdeeMisDeliveryRequest.builder()
                .batchNo("TEST-QTCK")
                .billDate(LocalDate.now())
                .deptCode(properties.getMisDeliveryDeptNumber())
                .lines(List.of(KingdeeMisDeliveryRequest.Line.builder()
                        .materialCode("MAT-TEST-001")
                        .unitCode(properties.getStockInDefaultUnitNumber())
                        .warehouseCode(properties.getStockInDefaultWarehouseNumber())
                        .quantity(BigDecimal.ONE)
                        .build()))
                .build();
    }
}
