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
 * 从收料通知单 View 解析源单内码，用于采购入库单关联。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KingdeeReceiveBillSourceResolver {

    private final KingdeeCloudService kingdeeCloudService;
    private final KingdeeCloudProperties properties;

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
            SourceContext.SourceContextBuilder builder = SourceContext.builder()
                    .billNo(bill.getBillNo())
                    .supplierCode(bill.getSupplierCode())
                    .billId(bill.getBillId());
            if (bill.getLines() == null || bill.getLines().isEmpty()) {
                return builder.build();
            }
            for (KingdeeReceiveBillLineVo line : bill.getLines()) {
                boolean lineMatch = lineNo != null && lineNo.equals(line.getLineNo());
                boolean materialMatch = StringUtils.hasText(materialCode)
                        && materialCode.equalsIgnoreCase(line.getMaterialCode());
                if (!lineMatch && !materialMatch) {
                    continue;
                }
                return builder
                        .entryId(line.getEntryId())
                        .entryLineNo(line.getLineNo())
                        .stockWarehouseCode(line.getStockWarehouseCode())
                        .unitCode(line.getUnitCode())
                        .batchNo(line.getBatchNo())
                        .remainInStockBaseQty(line.getRemainInStockBaseQty())
                        .baseUnitQty(line.getBaseUnitQty())
                        .poOrderNo(line.getPoOrderNo())
                        .poOrderEntryId(line.getPoOrderEntryId())
                        .build();
            }
        } catch (Exception e) {
            log.warn("Resolve receive bill source failed billNo={}: {}", receiveBillNo, e.getMessage());
        }
        return SourceContext.empty();
    }

    @Data
    @Builder
    public static class SourceContext {
        private String billNo;
        private String supplierCode;
        private Long billId;
        private Long entryId;
        private Integer entryLineNo;
        private String stockWarehouseCode;
        private String unitCode;
        private String batchNo;
        private BigDecimal remainInStockBaseQty;
        private BigDecimal baseUnitQty;
        private String poOrderNo;
        private Long poOrderEntryId;

        public static SourceContext empty() {
            return SourceContext.builder().build();
        }
    }
}
