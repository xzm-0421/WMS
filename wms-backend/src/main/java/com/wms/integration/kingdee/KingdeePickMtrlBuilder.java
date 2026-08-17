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
 * 按金蝶 PRD_PickMtrl Save 官方字段构建生产领料单 JSON。
 * formId = PRD_PickMtrl；源单为生产用料清单 PRD_PPBOM。
 */
public final class KingdeePickMtrlBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SRC_BILL_TYPE = "PRD_PPBOM";

    private KingdeePickMtrlBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props,
                               KingdeePickMtrlRequest req) {
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
            putNumberRef(model, "FBillType", props.getPickMtrlBillTypeNumber());
            model.put("FDate", formatBillDate(req.getBillDate()));
            putNumberRef(model, "FStockOrgId", props.getStockInOrgNumber());
            putNumberRef(model, "FPrdOrgId", props.getStockInOrgNumber());

            String workShop = firstNonBlank(req.getWorkShopCode(), props.getPickMtrlWorkShopNumber());
            if (StringUtils.hasText(workShop)) {
                putNumberRef(model, "FWorkShopId", workShop);
            }
            String headStock = firstNonBlank(req.getStockCode(),
                    firstLineWarehouse(req), props.getStockInDefaultWarehouseNumber());
            if (StringUtils.hasText(headStock)) {
                putNumberRef(model, "FStockId0", headStock);
            }
            model.put("FOwnerTypeId0", props.getStockInOwnerTypeHead());
            putNumberRef(model, "FOwnerId0", props.getStockInOrgNumber());
            putNumberRef(model, "FCurrId", props.getStockInSettleCurrNumber());
            model.put("FIsCrossTrade", false);
            model.put("FVmiBusiness", false);
            model.put("FIsOwnerTInclOrg", false);
            model.put("F_PrintTimes", 0);

            String note = firstNonBlank(req.getNote(), buildDefaultNote(req));
            if (StringUtils.hasText(note)) {
                model.put("FDescription", note);
            }

            ArrayNode entries = model.putArray("FEntity");
            List<KingdeePickMtrlRequest.Line> lines = req.getLines() == null ? List.of() : req.getLines();
            for (KingdeePickMtrlRequest.Line line : lines) {
                entries.add(buildEntry(mapper, props, req, line, workShop));
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶生产领料单保存请求失败", e);
        }
    }

    private static ObjectNode buildEntry(ObjectMapper mapper, KingdeeCloudProperties props,
                                         KingdeePickMtrlRequest req,
                                         KingdeePickMtrlRequest.Line line,
                                         String workShop) {
        ObjectNode entry = mapper.createObjectNode();
        BigDecimal qty = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        String unit = defaultUnit(line.getUnitCode(), props);
        String stock = firstNonBlank(line.getWarehouseCode(), props.getStockInDefaultWarehouseNumber());

        entry.put("FEntryID", 0);
        putNumberRef(entry, "FParentMaterialId", line.getParentMaterialCode());
        putNumberRef(entry, "FMaterialId", line.getMaterialCode());
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
        entry.put("FEntryVmiBusiness", false);
        entry.put("FCheckReturnMtrl", false);
        entry.put("FIsOverLegalOrg", false);
        entry.put("FConsome", "0");
        entry.put("FReserveType", "1");
        entry.put("FOptQueue", "0");
        entry.put("FOptPlanBillId", 0);
        entry.put("FOptDetailId", 0);
        entry.put("FOperId", 10);
        entry.put("FPickingStatus", 4);
        entry.put("FAllowOverQty", 0.0);
        entry.put("FBaseAllowOverQty", 0.0);
        entry.put("FStockAllowOverQty", 0.0);
        entry.put("FSecActualQty", 0.0);
        entry.put("FSecAllowOverQty", 0.0);
        entry.put("FPrice", 0.0);
        entry.put("FAmount", 0.0);

        putNumberRef(entry, "FStockId", stock);
        if (props.isPickMtrlSendStockLoc() && StringUtils.hasText(line.getLocationCode())) {
            putNumberRef(entry, "FStockLocId", line.getLocationCode());
        }
        putNumberRef(entry, "FStockStatusId", props.getStockInStockStatusNumber());
        String lot = BatchNoNormalizer.normalize(line.getBatchNo());
        if (StringUtils.hasText(lot)) {
            putNumberRef(entry, "FLot", lot);
        }

        entry.put("FOwnerTypeId", props.getStockInOwnerTypeHead());
        putNumberRef(entry, "FOwnerId", props.getStockInOrgNumber());
        entry.put("FKeeperTypeId", "BD_KeeperOrg");
        putNumberRef(entry, "FKeeperId", props.getStockInOrgNumber());
        entry.put("FParentOwnerTypeId", props.getStockInOwnerTypeHead());
        putNumberRef(entry, "FParentOwnerId", props.getStockInOrgNumber());

        if (StringUtils.hasText(workShop)) {
            putNumberRef(entry, "FEntryWorkShopId", workShop);
        }
        entry.put("FStockFlag", props.getPickMtrlStockFlag());

        String moBillNo = firstNonBlank(line.getMoBillNo());
        if (StringUtils.hasText(moBillNo)) {
            entry.put("FMoBillNo", moBillNo.trim());
        }
        putLong(entry, "FMoId", line.getMoId());
        putLong(entry, "FMoEntryId", line.getMoEntryId());
        if (line.getMoEntrySeq() != null && line.getMoEntrySeq() > 0) {
            entry.put("FMoEntrySeq", line.getMoEntrySeq());
        }

        String ppBomBillNo = firstNonBlank(line.getPpBomBillNo(), req.getSourceBillNo());
        if (StringUtils.hasText(ppBomBillNo)) {
            entry.put("FPPBomBillNo", ppBomBillNo.trim());
            entry.put("FSrcBillNo", ppBomBillNo.trim());
        }
        entry.put("FSrcBillType", SRC_BILL_TYPE);
        Long ppBomBillId = firstNonNull(line.getPpBomBillId(), req.getSourceBillId());
        Long ppBomEntryId = firstNonNull(line.getPpBomEntryId());
        putLong(entry, "FPPBomEntryId", ppBomEntryId);
        putLong(entry, "FEntrySrcInterId", ppBomBillId);
        putLong(entry, "FEntrySrcEnteryId", ppBomEntryId);
        if (line.getSourceLineNo() != null && line.getSourceLineNo() > 0) {
            entry.put("FEntrySrcEntrySeq", line.getSourceLineNo());
        }

        String entryNote = firstNonBlank(line.getEntryNote(), line.getMaterialName());
        if (StringUtils.hasText(entryNote)) {
            entry.put("FEntrtyMemo", entryNote.trim());
        }

        if (ppBomBillId != null && ppBomBillId > 0 && ppBomEntryId != null && ppBomEntryId > 0) {
            ArrayNode links = entry.putArray("FEntity_Link");
            ObjectNode link = links.addObject();
            link.put("FEntity_Link_FFlowId", blankAsSpace(props.getPickMtrlPpBomLinkFlowId()));
            link.put("FEntity_Link_FFlowLineId", props.getPickMtrlPpBomLinkFlowLineId());
            link.put("FEntity_Link_FRuleId", props.getPickMtrlPpBomLinkRuleId());
            link.put("FEntity_Link_FSTableName", props.getPickMtrlPpBomLinkSTableName());
            link.put("FEntity_Link_FSBillId", String.valueOf(ppBomBillId));
            link.put("FEntity_Link_FSId", String.valueOf(ppBomEntryId));
            link.put("FEntity_Link_FBaseActualQtyOld", qty);
            link.put("FEntity_Link_FBaseActualQty", qty);
        }
        return entry;
    }

    private static String firstLineWarehouse(KingdeePickMtrlRequest req) {
        if (req.getLines() == null || req.getLines().isEmpty()) {
            return null;
        }
        return req.getLines().get(0).getWarehouseCode();
    }

    private static String buildDefaultNote(KingdeePickMtrlRequest req) {
        StringBuilder sb = new StringBuilder("WMS PDA生产领料");
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
        return d.atStartOfDay().format(DATE_TIME);
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
