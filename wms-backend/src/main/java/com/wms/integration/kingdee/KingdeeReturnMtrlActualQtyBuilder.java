package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新已有生产/委外退料单分录实退数量（FQty 等）。
 * 仅更新已有分录，不删行、不新建。
 * <p>NeedUpDateFields 须含 {@code FEntity}，否则金蝶可能判定无分录可更新、实退不落库。
 */
public final class KingdeeReturnMtrlActualQtyBuilder {

    private KingdeeReturnMtrlActualQtyBuilder() {
    }

    public static String build(ObjectMapper mapper, Long billId, String billNo, List<Line> lines) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode needUpdate = root.putArray("NeedUpDateFields");
            // 分录体键 + 数量字段（扁平字段名）
            needUpdate.add("FEntity");
            needUpdate.add("FQty");
            needUpdate.add("FBaseQty");
            needUpdate.add("FStockQty");
            needUpdate.add("FBASESTOCKQTY");
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

            ArrayNode entries = model.putArray("FEntity");
            if (lines != null) {
                for (Line line : lines) {
                    if (line == null || line.getEntryId() == null || line.getEntryId() <= 0) {
                        continue;
                    }
                    BigDecimal qty = line.getActualQty() != null ? line.getActualQty() : BigDecimal.ZERO;
                    ObjectNode entry = entries.addObject();
                    entry.put("FEntryID", line.getEntryId());
                    entry.put("FQty", qty);
                    entry.put("FBaseQty", qty);
                    entry.put("FStockQty", qty);
                    entry.put("FBASESTOCKQTY", qty);
                }
            }
            if (entries.isEmpty()) {
                throw new IllegalStateException("退料实退 Save 报文缺少有效分录（FEntryID）");
            }
            return mapper.writeValueAsString(root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("构建退料实退数量 Save 报文失败", e);
        }
    }

    public static final class Line {
        private final Long entryId;
        private final BigDecimal actualQty;

        public Line(Long entryId, BigDecimal actualQty) {
            this.entryId = entryId;
            this.actualQty = actualQty;
        }

        public Long getEntryId() {
            return entryId;
        }

        public BigDecimal getActualQty() {
            return actualQty;
        }
    }
}
