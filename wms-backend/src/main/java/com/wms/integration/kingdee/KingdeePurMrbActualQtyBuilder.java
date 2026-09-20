package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新已有采购退料单（PUR_MRB）分录实退数量 FRMREALQTY。
 * 仅更新已有分录，不删行、不新建。
 * <p>分录体键为 {@code FPURMRBENTRY}（非 FEntity），NeedUpDateFields 须含该键，
 * 否则金蝶会判定无分录可更新、实退数量不落库。
 * <p>基本单位数量交由金蝶按 IsAutoAdjustField 自行换算，避免账套字段差异导致 Save 失败。
 */
public final class KingdeePurMrbActualQtyBuilder {

    private static final String ENTRY_KEY = "FPURMRBENTRY";
    private static final String QTY_FIELD = "FRMREALQTY";

    private KingdeePurMrbActualQtyBuilder() {
    }

    public static String build(ObjectMapper mapper, Long billId, String billNo, List<Line> lines) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode needUpdate = root.putArray("NeedUpDateFields");
            needUpdate.add(ENTRY_KEY);
            needUpdate.add(QTY_FIELD);
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

            ArrayNode entries = model.putArray(ENTRY_KEY);
            if (lines != null) {
                for (Line line : lines) {
                    if (line == null || line.getEntryId() == null || line.getEntryId() <= 0) {
                        continue;
                    }
                    BigDecimal qty = line.getActualQty() != null ? line.getActualQty() : BigDecimal.ZERO;
                    ObjectNode entry = entries.addObject();
                    entry.put("FEntryID", line.getEntryId());
                    entry.put(QTY_FIELD, qty);
                }
            }
            if (entries.isEmpty()) {
                throw new IllegalStateException("采购退料实退 Save 报文缺少有效分录（FEntryID）");
            }
            return mapper.writeValueAsString(root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("构建采购退料实退数量 Save 报文失败", e);
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
