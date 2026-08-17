package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 从收料通知单 View 解析源单内码与送货单号，用于采购入库单关联。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KingdeeReceiveBillSourceResolver {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;

    /**
     * 解析收料通知单（整单 View 一次）。lineNo/materialCode 可空：仅返回表头上下文（含 F_QVHU_Text_qtr）。
     */
    public SourceContext resolve(String receiveBillNo, Integer lineNo, String materialCode) {
        if (!kingdeeCloudService.isEnabled() || !StringUtils.hasText(receiveBillNo)) {
            return SourceContext.empty();
        }
        try {
            JsonNode billNode = kingdeeCloudService.viewBill(
                    properties.getReceiveBillFormId(), receiveBillNo.trim());
            KingdeeReceiveBillVo bill = KingdeeReceiveBillViewParser.parse(billNode);
            if (bill == null) {
                return SourceContext.empty();
            }
            SourceContext header = toHeaderContext(bill);
            if (bill.getLines() == null || bill.getLines().isEmpty()) {
                return header;
            }
            boolean needLine = lineNo != null || StringUtils.hasText(materialCode);
            if (!needLine) {
                return header;
            }
            for (KingdeeReceiveBillLineVo line : bill.getLines()) {
                boolean lineMatch = lineNo != null && lineNo.equals(line.getLineNo());
                boolean materialMatch = StringUtils.hasText(materialCode)
                        && materialCode.equalsIgnoreCase(line.getMaterialCode());
                if (!lineMatch && !materialMatch) {
                    continue;
                }
                return toLineContext(bill, line);
            }
            log.warn("Resolve receive bill line not found billNo={} lineNo={} material={}",
                    receiveBillNo, lineNo, materialCode);
            return header;
        } catch (Exception e) {
            log.warn("Resolve receive bill source failed billNo={}: {}", receiveBillNo, e.getMessage());
        }
        return SourceContext.empty();
    }

    /**
     * 一次 View 解析整单，供采购入库组装时复用，避免按行重复拉单。
     */
    public KingdeeReceiveBillVo loadBill(String receiveBillNo) {
        if (!kingdeeCloudService.isEnabled() || !StringUtils.hasText(receiveBillNo)) {
            return null;
        }
        try {
            JsonNode billNode = kingdeeCloudService.viewBill(
                    properties.getReceiveBillFormId(), receiveBillNo.trim());
            return KingdeeReceiveBillViewParser.parse(billNode);
        } catch (Exception e) {
            log.warn("Load receive bill failed billNo={}: {}", receiveBillNo, e.getMessage());
            return null;
        }
    }

    public static SourceContext toHeaderContext(KingdeeReceiveBillVo bill) {
        if (bill == null) {
            return SourceContext.empty();
        }
        return SourceContext.builder()
                .billNo(bill.getBillNo())
                .supplierCode(bill.getSupplierCode())
                .billId(bill.getBillId())
                .sendBillNo(bill.getSendBillNo())
                .businessType(bill.getBusinessType())
                .billTypeNumber(bill.getBillTypeNumber())
                .build();
    }

    public static SourceContext toLineContext(KingdeeReceiveBillVo bill, KingdeeReceiveBillLineVo line) {
        if (bill == null) {
            return SourceContext.empty();
        }
        if (line == null) {
            return toHeaderContext(bill);
        }
        return SourceContext.builder()
                .billNo(bill.getBillNo())
                .supplierCode(bill.getSupplierCode())
                .billId(bill.getBillId())
                .businessType(bill.getBusinessType())
                .billTypeNumber(bill.getBillTypeNumber())
                .entryId(line.getEntryId())
                .entryLineNo(line.getLineNo())
                .materialCode(line.getMaterialCode())
                .stockWarehouseCode(line.getStockWarehouseCode())
                .unitCode(line.getUnitCode())
                .priceUnitCode(line.getPriceUnitCode())
                .batchNo(line.getBatchNo())
                .remainInStockBaseQty(line.getRemainInStockBaseQty())
                .baseUnitQty(line.getBaseUnitQty())
                .sendBillNo(firstNonBlank(line.getSendBillNo(), bill.getSendBillNo()))
                .poOrderNo(line.getPoOrderNo())
                .poOrderEntryId(line.getPoOrderEntryId())
                .subReqBillNo(line.getSubReqBillNo())
                .build();
    }

    @Data
    @Builder
    public static class SourceContext {
        private String billNo;
        private String supplierCode;
        private Long billId;
        private String businessType;
        private String billTypeNumber;
        private Long entryId;
        private Integer entryLineNo;
        private String materialCode;
        private String stockWarehouseCode;
        private String unitCode;
        private String priceUnitCode;
        private String batchNo;
        private BigDecimal remainInStockBaseQty;
        private BigDecimal baseUnitQty;
        /** 送货单号 F_QVHU_Text_qtr（来自收料通知单表头/分录） */
        private String sendBillNo;
        private String poOrderNo;
        private Long poOrderEntryId;
        private String subReqBillNo;

        public static SourceContext empty() {
            return SourceContext.builder().build();
        }
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
}
