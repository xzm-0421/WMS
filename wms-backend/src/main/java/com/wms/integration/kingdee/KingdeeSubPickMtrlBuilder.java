package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.barcode.util.BatchNoNormalizer;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 按金蝶 SUB_PickMtrl Save 官方字段构建委外领料单 JSON。
 * 源单为委外用料清单 SUB_PPBOM。
 */
public final class KingdeeSubPickMtrlBuilder {

    private static final DateTimeFormatter DATE_ONLY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SRC_BILL_TYPE = "SUB_PPBOM";

    private KingdeeSubPickMtrlBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props,
                               KingdeeSubPickMtrlRequest req) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.putArray("NeedUpDateFields");
            ArrayNode needReturn = root.putArray("NeedReturnFields");
            needReturn.add("FBillNo");
            needReturn.add("FID");
            root.put("IsDeleteEntry", "true");
            root.put("SubSystemId", "");
            root.put("IsVerifyBaseDataField", "false");
            root.put("IsEntryBatchFill", "true");
            root.put("ValidateFlag", "true");
            root.put("NumberSearch", "true");
            root.put("IsAutoAdjustField", "true");
            root.put("InterationFlags", "");
            root.put("IgnoreInterationFlag", "");
            root.put("IsControlPrecision", "false");
            root.put("ValidateRepeatJson", "true");

            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            putNumberRef(model, "FBillType", props.getSubPickMtrlBillTypeNumber());
            model.put("FDate", formatBillDate(req.getBillDate()));
            putNumberRef(model, "FStockOrgId", props.getStockInOrgNumber());
            putNumberRef(model, "FSubOrgId", props.getStockInOrgNumber());

            String supplier = firstNonBlank(req.getSupplierCode(), props.getSubPickMtrlDefaultSupplierNumber());
            if (StringUtils.hasText(supplier)) {
                putNumberRef(model, "FSupplierId", supplier);
            }
            String headStock = firstNonBlank(req.getStockCode(),
                    firstLineWarehouse(req), props.getStockInDefaultWarehouseNumber());
            if (StringUtils.hasText(headStock)) {
                putNumberRef(model, "FStockId0", headStock);
            }
            model.put("FIsCrossTrade", "false");
            model.put("FVmiBusiness", "false");
            model.put("FIsOwnerTInclOrg", "false");

            String note = firstNonBlank(req.getNote(), buildDefaultNote(req));
            if (StringUtils.hasText(note)) {
                model.put("FDescription", note);
            }

            ArrayNode entries = model.putArray("FEntity");
            List<KingdeeSubPickMtrlRequest.Line> lines = req.getLines() == null ? List.of() : req.getLines();
            for (KingdeeSubPickMtrlRequest.Line line : lines) {
                entries.add(buildEntry(mapper, props, req, line, supplier));
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶委外领料单保存请求失败", e);
        }
    }

    private static ObjectNode buildEntry(ObjectMapper mapper, KingdeeCloudProperties props,
                                         KingdeeSubPickMtrlRequest req,
                                         KingdeeSubPickMtrlRequest.Line line,
                                         String supplier) {
        ObjectNode entry = mapper.createObjectNode();
        BigDecimal qty = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        String unit = defaultUnit(line.getUnitCode(), props);
        String stock = firstNonBlank(line.getWarehouseCode(), props.getStockInDefaultWarehouseNumber());

        entry.put("FEntryID", 0);
        putNumberRef(entry, "FMaterialId", line.getMaterialCode());
        putNumberRef(entry, "FParentMaterialId", line.getParentMaterialCode());
        putNumberRef(entry, "FUnitID", unit);
        putNumberRef(entry, "FBaseUnitId", unit);
        putNumberRef(entry, "FStockUnitId", unit);

        entry.put("FAppQty", qty);
        entry.put("FActualQty", qty);
        entry.put("FBaseAppQty", qty);
        entry.put("FBaseActualQty", qty);
        entry.put("FStockAppQty", qty);
        entry.put("FStockActualQty", qty);
        entry.put("FBaseStockActualQty", qty);
        entry.put("FEntryVmiBusiness", "false");
        entry.put("FCheckSubRtnMtrl", "false");
        entry.put("FIsOverLegalOrg", "false");
        entry.put("FPickingStatus", 0);

        putNumberRef(entry, "FStockId", stock);
        if (props.isSubPickMtrlSendStockLoc() && StringUtils.hasText(line.getLocationCode())) {
            putNumberRef(entry, "FStockLocId", line.getLocationCode());
        }
        putNumberRef(entry, "FStockStatusId", props.getStockInStockStatusNumber());
        String lot = BatchNoNormalizer.normalize(line.getBatchNo());
        if (StringUtils.hasText(lot)) {
            putNumberRef(entry, "FLOT", lot);
        }

        entry.put("FOwnerTypeId", props.getStockInOwnerTypeHead());
        putNumberRef(entry, "FOwnerId", props.getStockInOrgNumber());
        entry.put("FKeeperTypeId", "BD_KeeperOrg");
        putNumberRef(entry, "FKeeperId", props.getStockInOrgNumber());
        entry.put("FParentOwnerTypeId", props.getStockInOwnerTypeHead());
        putNumberRef(entry, "FParentOwnerId", props.getStockInOrgNumber());
        putNumberRef(entry, "FSettleOrgId", props.getStockInOrgNumber());
        if (StringUtils.hasText(supplier)) {
            putNumberRef(entry, "FSupplierId0", supplier);
        }
        entry.put("FStockFlag", props.getSubPickMtrlStockFlag());

        String subReqBillNo = firstNonBlank(line.getSubReqBillNo());
        if (StringUtils.hasText(subReqBillNo)) {
            entry.put("FSubReqBillNo", subReqBillNo.trim());
        }
        putLong(entry, "FSubReqId", line.getSubReqId());
        putLong(entry, "FSubReqEntryId", line.getSubReqEntryId());
        if (line.getSubReqEntrySeq() != null && line.getSubReqEntrySeq() > 0) {
            entry.put("FSubReqEntrySeq", line.getSubReqEntrySeq());
        }

        String ppBomBillNo = firstNonBlank(line.getPpBomBillNo(), req.getSourceBillNo());
        if (StringUtils.hasText(ppBomBillNo)) {
            entry.put("FPPbomBillNo", ppBomBillNo.trim());
            entry.put("FSrcBillNo", ppBomBillNo.trim());
        }
        entry.put("FSrcBillType", SRC_BILL_TYPE);
        Long ppBomBillId = firstNonNull(line.getPpBomBillId(), req.getSourceBillId());
        Long ppBomEntryId = firstNonNull(line.getPpBomEntryId());
        putLong(entry, "FPPbomEntryId", ppBomEntryId);
        putLong(entry, "FSrcInterId", ppBomBillId);
        putLong(entry, "FSrcEntryId", ppBomEntryId);
        if (line.getSourceLineNo() != null && line.getSourceLineNo() > 0) {
            entry.put("FSrcEntrySeq", line.getSourceLineNo());
        }

        String entryNote = firstNonBlank(line.getEntryNote(), line.getMaterialName());
        if (StringUtils.hasText(entryNote)) {
            entry.put("FEntrtyMemo", entryNote.trim());
        }

        if (ppBomBillId != null && ppBomBillId > 0 && ppBomEntryId != null && ppBomEntryId > 0) {
            ArrayNode links = entry.putArray("FEntity_Link");
            ObjectNode link = links.addObject();
            link.put("FEntity_Link_FFlowId", blankAsSpace(props.getSubPickMtrlPpBomLinkFlowId()));
            link.put("FEntity_Link_FFlowLineId", props.getSubPickMtrlPpBomLinkFlowLineId());
            link.put("FEntity_Link_FRuleId", props.getSubPickMtrlPpBomLinkRuleId());
            link.put("FEntity_Link_FSTableName", props.getSubPickMtrlPpBomLinkSTableName());
            link.put("FEntity_Link_FSBillId", String.valueOf(ppBomBillId));
            link.put("FEntity_Link_FSId", String.valueOf(ppBomEntryId));
            link.put("FEntity_Link_FBaseActualQtyOld", qty);
            link.put("FEntity_Link_FBaseActualQty", qty);
        }
        return entry;
    }

    private static String firstLineWarehouse(KingdeeSubPickMtrlRequest req) {
        if (req.getLines() == null || req.getLines().isEmpty()) {
            return null;
        }
        return req.getLines().get(0).getWarehouseCode();
    }

    private static String buildDefaultNote(KingdeeSubPickMtrlRequest req) {
        StringBuilder sb = new StringBuilder("WMS PDA委外领料");
        if (StringUtils.hasText(req.getSourceBillNo())) {
            sb.append(" 用料清单 ").append(req.getSourceBillNo().trim());
        }
        if (StringUtils.hasText(req.getBatchNo())) {
            sb.append(" 批次").append(req.getBatchNo().trim());
        }
        return sb.toString();
    }

    private static String formatBillDate(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return d.format(DATE_ONLY);
    }

    private static String defaultUnit(String unitCode, KingdeeCloudProperties props) {
        return StringUtils.hasText(unitCode) ? unitCode.trim() : props.getStockInDefaultUnitNumber();
    }

    private static void putNumberRef(ObjectNode parent, String field, String number) {
        if (!StringUtils.hasText(number)) {
            return;
        }
        ObjectNode ref = parent.putObject(field);
        ref.put("FNumber", number.trim());
    }

    private static void putLong(ObjectNode parent, String field, Long value) {
        if (value == null || value <= 0) {
            return;
        }
        parent.put(field, value);
    }

    private static Long firstNonNull(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private static String blankAsSpace(String value) {
        return StringUtils.hasText(value) ? value.trim() : " ";
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
