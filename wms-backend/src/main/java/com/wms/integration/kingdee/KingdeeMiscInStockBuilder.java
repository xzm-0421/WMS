package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wms.barcode.util.BatchNoNormalizer;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 其他入库单 STK_Miscellaneous Save。
 */
public final class KingdeeMiscInStockBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private KingdeeMiscInStockBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props, KingdeeMiscInStockRequest req) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            if (StringUtils.hasText(req.getBillNo())) {
                model.put("FBillNo", req.getBillNo().trim());
            }
            KingdeeSaveEnvelope.putNumberRef(model, "FBillTypeID", props.getMiscInStockBillTypeNumber());
            KingdeeSaveEnvelope.putNumberRef(model, "FStockOrgId", props.getStockInOrgNumber());
            model.put("FStockDirect", KingdeeSaveEnvelope.firstNonBlank(req.getStockDirect(), "GENERAL"));
            model.put("FDate", formatBillDate(req.getBillDate()));
            KingdeeSaveEnvelope.putNumberRef(model, "FSUPPLIERID",
                    KingdeeSaveEnvelope.firstNonBlank(req.getSupplierCode(), props.getStockInDefaultSupplierNumber()));
            KingdeeSaveEnvelope.putNumberRef(model, "FDEPTID",
                    KingdeeSaveEnvelope.firstNonBlank(req.getDeptCode(), props.getMiscInStockDeptNumber()));
            KingdeeSaveEnvelope.putNumberRef(model, "FSTOCKERID", props.getStockInStockerNumber());
            model.put("FOwnerTypeIdHead", props.getStockInOwnerTypeHead());
            KingdeeSaveEnvelope.putNumberRef(model, "FOwnerIdHead", props.getStockInOrgNumber());
            if (StringUtils.hasText(req.getNote())) {
                model.put("FNOTE", req.getNote().trim());
            }

            ArrayNode entries = model.putArray("FEntity");
            List<KingdeeMiscInStockRequest.Line> lines = req.getLines() == null ? List.of() : req.getLines();
            for (KingdeeMiscInStockRequest.Line line : lines) {
                entries.add(buildEntry(mapper, props, line));
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶其他入库单保存请求失败", e);
        }
    }

    private static ObjectNode buildEntry(ObjectMapper mapper, KingdeeCloudProperties props,
                                         KingdeeMiscInStockRequest.Line line) {
        ObjectNode entry = mapper.createObjectNode();
        BigDecimal qty = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        String unit = StringUtils.hasText(line.getUnitCode())
                ? line.getUnitCode().trim() : props.getStockInDefaultUnitNumber();
        String stock = KingdeeSaveEnvelope.firstNonBlank(line.getWarehouseCode(), props.getStockInDefaultWarehouseNumber());

        entry.put("FEntryID", 0);
        entry.put("FInStockType", KingdeeSaveEnvelope.firstNonBlank(line.getInStockType(), "1"));
        KingdeeSaveEnvelope.putNumberRef(entry, "FMATERIALID", line.getMaterialCode());
        KingdeeSaveEnvelope.putNumberRef(entry, "FUnitID", unit);
        KingdeeSaveEnvelope.putNumberRef(entry, "FSTOCKID", stock);
        KingdeeSaveEnvelope.putNumberRef(entry, "FSTOCKSTATUSID",
                KingdeeSaveEnvelope.firstNonBlank(line.getStockStatusNumber(), props.getStockInStockStatusNumber()));
        String lot = BatchNoNormalizer.normalize(line.getBatchNo());
        if (StringUtils.hasText(lot)) {
            KingdeeSaveEnvelope.putNumberRef(entry, "FLOT", lot);
        }
        entry.put("FQty", qty);
        if (StringUtils.hasText(line.getEntryNote())) {
            entry.put("FEntryNote", line.getEntryNote().trim());
        }
        entry.put("FOWNERTYPEID", KingdeeSaveEnvelope.firstNonBlank(line.getOwnerTypeId(), props.getStockInOwnerTypeHead()));
        KingdeeSaveEnvelope.putNumberRef(entry, "FOWNERID",
                KingdeeSaveEnvelope.firstNonBlank(line.getOwnerId(), props.getStockInOrgNumber()));
        entry.put("FKEEPERTYPEID", KingdeeSaveEnvelope.firstNonBlank(line.getKeeperTypeId(), "BD_KeeperOrg"));
        KingdeeSaveEnvelope.putNumberRef(entry, "FKEEPERID",
                KingdeeSaveEnvelope.firstNonBlank(line.getKeeperId(), props.getStockInOrgNumber()));
        if (props.isMiscInStockSendStockLoc() && StringUtils.hasText(line.getLocationCode())) {
            ObjectNode loc = entry.putObject("FStockLocId");
            ObjectNode flex = loc.putObject("FSTOCKLOCID__FF100001");
            flex.put("FNumber", line.getLocationCode().trim());
        }
        return entry;
    }

    private static String formatBillDate(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return LocalDateTime.of(d.getYear(), d.getMonth(), d.getDayOfMonth(), 0, 0, 0).format(DATE_TIME);
    }
}
