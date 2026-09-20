package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新物料盘点作业（STK_StockCountInput）分录盘点数量 FCountQty。
 * 仅更新已有分录，不删行。
 */
public final class KingdeeStockCountCountQtyBuilder {

    private KingdeeStockCountCountQtyBuilder() {
    }

    public static String build(ObjectMapper mapper, Long billId, String billNo, List<Line> lines) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode needUpdate = root.putArray("NeedUpDateFields");
            needUpdate.add("FCountQty");
            ArrayNode needReturn = root.putArray("NeedReturnFields");
            needReturn.add("FBillNo");
            needReturn.add("FID");
            root.put("IsDeleteEntry", "false");
            root.put("SubSystemId", "");
            root.put("IsVerifyBaseDataField", "false");
            root.put("IsEntryBatchFill", "false");
            root.put("ValidateFlag", "true");
            root.put("NumberSearch", "true");
            root.put("IsAutoAdjustField", "true");
            root.put("InterationFlags", "");
            root.put("IgnoreInterationFlag", "");
            root.put("IsControlPrecision", "false");
            root.put("ValidateRepeatJson", "true");

            ObjectNode model = root.putObject("Model");
            if (billId != null && billId > 0) {
                model.put("FID", billId);
            }
            if (StringUtils.hasText(billNo)) {
                model.put("FBillNo", billNo.trim());
            }

            ArrayNode entries = model.putArray("FBillEntry");
            if (lines != null) {
                for (Line line : lines) {
                    if (line == null || line.getEntryId() == null || line.getEntryId() <= 0) {
                        continue;
                    }
                    ObjectNode entry = entries.addObject();
                    entry.put("FEntryID", line.getEntryId());
                    entry.put("FCountQty", line.getCountQty() != null ? line.getCountQty() : BigDecimal.ZERO);
                }
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建盘点数量 Save 报文失败", e);
        }
    }

    public static final class Line {
        private final Long entryId;
        private final BigDecimal countQty;

        public Line(Long entryId, BigDecimal countQty) {
            this.entryId = entryId;
            this.countQty = countQty;
        }

        public Long getEntryId() {
            return entryId;
        }

        public BigDecimal getCountQty() {
            return countQty;
        }
    }
}
