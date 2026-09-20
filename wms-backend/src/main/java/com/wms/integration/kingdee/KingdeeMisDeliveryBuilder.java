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
 * 其他出库单 STK_MisDelivery Save。
 */
public final class KingdeeMisDeliveryBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private KingdeeMisDeliveryBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props, KingdeeMisDeliveryRequest req) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            if (StringUtils.hasText(req.getBillNo())) {
                model.put("FBillNo", req.getBillNo().trim());
            }
            KingdeeSaveEnvelope.putNumberRef(model, "FBillTypeID", props.getMisDeliveryBillTypeNumber());
            KingdeeSaveEnvelope.putNumberRef(model, "FStockOrgId", props.getStockInOrgNumber());
            KingdeeSaveEnvelope.putNumberRef(model, "FPickOrgId", props.getStockInOrgNumber());
            model.put("FStockDirect", KingdeeSaveEnvelope.firstNonBlank(req.getStockDirect(), "GENERAL"));
            model.put("FDate", formatBillDate(req.getBillDate()));
            KingdeeSaveEnvelope.putNumberRef(model, "FCustId", req.getCustomerCode());
            KingdeeSaveEnvelope.putNumberRef(model, "FDeptId",
                    KingdeeSaveEnvelope.firstNonBlank(req.getDeptCode(), props.getMisDeliveryDeptNumber()));
            KingdeeSaveEnvelope.putNumberRef(model, "FStockerId", props.getStockInStockerNumber());
            model.put("FBizType", KingdeeSaveEnvelope.firstNonBlank(req.getBizType(), props.getMisDeliveryBizType()));
            model.put("FOwnerTypeIdHead", props.getStockInOwnerTypeHead());
            KingdeeSaveEnvelope.putNumberRef(model, "FOwnerIdHead", props.getStockInOrgNumber());
            if (StringUtils.hasText(req.getNote())) {
                model.put("FNote", req.getNote().trim());
            }
            KingdeeSaveEnvelope.putNumberRef(model, "FSUPPLIERID",
                    KingdeeSaveEnvelope.firstNonBlank(req.getSupplierCode(), props.getStockInDefaultSupplierNumber()));

            ArrayNode entries = model.putArray("FEntity");
            List<KingdeeMisDeliveryRequest.Line> lines = req.getLines() == null ? List.of() : req.getLines();
            for (KingdeeMisDeliveryRequest.Line line : lines) {
                entries.add(buildEntry(mapper, props, line));
            }
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶其他出库单保存请求失败", e);
        }
    }

    private static ObjectNode buildEntry(ObjectMapper mapper, KingdeeCloudProperties props,
                                         KingdeeMisDeliveryRequest.Line line) {
        ObjectNode entry = mapper.createObjectNode();
        BigDecimal qty = line.getQuantity() == null ? BigDecimal.ZERO : line.getQuantity();
        String unit = StringUtils.hasText(line.getUnitCode())
                ? line.getUnitCode().trim() : props.getStockInDefaultUnitNumber();
        String stock = KingdeeSaveEnvelope.firstNonBlank(line.getWarehouseCode(), props.getStockInDefaultWarehouseNumber());

        entry.put("FEntryID", 0);
        KingdeeSaveEnvelope.putNumberRef(entry, "FMaterialId", line.getMaterialCode());
        KingdeeSaveEnvelope.putNumberRef(entry, "FUnitID", unit);
        entry.put("FQty", qty);
        KingdeeSaveEnvelope.putNumberRef(entry, "FBaseUnitId", unit);
        KingdeeSaveEnvelope.putNumberRef(entry, "FStockId", stock);
        String lot = BatchNoNormalizer.normalize(line.getBatchNo());
        if (StringUtils.hasText(lot)) {
            KingdeeSaveEnvelope.putNumberRef(entry, "FLot", lot);
        }
        entry.put("FOwnerTypeId", KingdeeSaveEnvelope.firstNonBlank(line.getOwnerTypeId(), props.getStockInOwnerTypeHead()));
        KingdeeSaveEnvelope.putNumberRef(entry, "FOwnerId",
                KingdeeSaveEnvelope.firstNonBlank(line.getOwnerId(), props.getStockInOrgNumber()));
        KingdeeSaveEnvelope.putNumberRef(entry, "FStockStatusId",
                KingdeeSaveEnvelope.firstNonBlank(line.getStockStatusNumber(), props.getStockInStockStatusNumber()));
        entry.put("FKeeperTypeId", KingdeeSaveEnvelope.firstNonBlank(line.getKeeperTypeId(), "BD_KeeperOrg"));
        KingdeeSaveEnvelope.putNumberRef(entry, "FKeeperId",
                KingdeeSaveEnvelope.firstNonBlank(line.getKeeperId(), props.getStockInOrgNumber()));
        entry.put("FDistribution", "false");
        if (StringUtils.hasText(line.getEntryNote())) {
            entry.put("FEntryNote", line.getEntryNote().trim());
        }
        if (props.isMisDeliverySendStockLoc() && StringUtils.hasText(line.getLocationCode())) {
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
