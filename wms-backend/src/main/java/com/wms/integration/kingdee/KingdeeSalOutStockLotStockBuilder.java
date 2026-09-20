package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 更新已有销售出库单分录批号 FLot、仓库 FStockId（及可选实发数量）。
 * 用于发货通知下推后补全必录字段再 Submit+Audit。
 * <p>仓库字段须为 {@code FStockId}（与 SAL_OUTSTOCK / 退货单一致），勿写 FStockID，否则金蝶不落库。
 */
public final class KingdeeSalOutStockLotStockBuilder {

    private KingdeeSalOutStockLotStockBuilder() {
    }

    public static String build(ObjectMapper mapper, Long billId, String billNo, List<EntryUpdate> entries) {
        try {
            ObjectNode root = mapper.createObjectNode();
            ArrayNode needUpdate = root.putArray("NeedUpDateFields");
            needUpdate.add("FEntity");
            needUpdate.add("FLot");
            needUpdate.add("FStockId");
            needUpdate.add("FRealQty");
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

            ArrayNode entityArr = model.putArray("FEntity");
            if (entries != null) {
                for (EntryUpdate line : entries) {
                    if (line == null || line.entryId() == null || line.entryId() <= 0) {
                        continue;
                    }
                    ObjectNode entry = entityArr.addObject();
                    entry.put("FEntryID", line.entryId());
                    if (StringUtils.hasText(line.lotNumber())) {
                        KingdeeSaveEnvelope.putNumberRef(entry, "FLot", line.lotNumber());
                    }
                    if (StringUtils.hasText(line.stockNumber())) {
                        KingdeeSaveEnvelope.putNumberRef(entry, "FStockId", line.stockNumber());
                    }
                    if (line.realQty() != null && line.realQty().compareTo(BigDecimal.ZERO) > 0) {
                        entry.put("FRealQty", line.realQty());
                    }
                }
            }
            if (entityArr.isEmpty()) {
                throw new IllegalStateException("销售出库批号/仓库 Save 缺少有效分录（FEntryID）");
            }
            return mapper.writeValueAsString(root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("构建销售出库批号/仓库 Save 报文失败", e);
        }
    }

    public record EntryUpdate(Long entryId, String lotNumber, String stockNumber, BigDecimal realQty) {
    }
}
