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
 * 生产入库单 PRD_INSTOCK Save。
 * 默认源单=生产汇报 PRD_MORPT，写入 FEntity_Link（PRD_MORPT2INSTOCK）以便反写入库选单量。
 */
public final class KingdeePrdInStockBuilder {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String SRC_BILL_TYPE_MORPT = "PRD_MORPT";

    private KingdeePrdInStockBuilder() {
    }

    public static String build(ObjectMapper mapper, KingdeeCloudProperties props, KingdeePrdInStockRequest req) {
        try {
            ObjectNode root = KingdeeSaveEnvelope.createRoot(mapper);
            ObjectNode model = root.putObject("Model");
            model.put("FID", 0);
            KingdeeSaveEnvelope.putNumberRef(model, "FBillType", props.getPrdInStockBillTypeNumber());
            model.put("FDate", formatBillDate(req.getBillDate()));
            KingdeeSaveEnvelope.putNumberRef(model, "FStockOrgId", props.getStockInOrgNumber());
            KingdeeSaveEnvelope.putNumberRef(model, "FPrdOrgId", props.getStockInOrgNumber());
            model.put("FOwnerTypeId0", props.getStockInOwnerTypeHead());
            KingdeeSaveEnvelope.putNumberRef(model, "FOwnerId0", props.getStockInOrgNumber());
            model.put("FIsEntrust", false);
            KingdeeSaveEnvelope.putNumberRef(model, "FCurrId", props.getStockInSettleCurrNumber());
            model.put("FEntrustInStockId", 0);
            model.put("FPrintTimes", 0);

            String note = KingdeeSaveEnvelope.firstNonBlank(req.getNote(), buildDefaultNote(req));
            if (StringUtils.hasText(note)) {
                model.put("FDescription", note);
            }

            ArrayNode entries = model.putArray("FEntity");
            List<KingdeePrdInStockRequest.Line> lines = req.getLines() == null ? List.of() : req.getLines();
            if (lines.isEmpty()) {
                throw new IllegalStateException("生产入库单明细不能为空");
            }
            for (KingdeePrdInStockRequest.Line line : lines) {
                entries.add(buildEntry(mapper, props, line, req.getWorkShopCode()));
            }
            return mapper.writeValueAsString(root);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("构建金蝶生产入库单保存请求失败", e);
        }
    }

    private static ObjectNode buildEntry(ObjectMapper mapper, KingdeeCloudProperties props,
                                         KingdeePrdInStockRequest.Line line, String reqWorkShop) {
        ObjectNode entry = mapper.createObjectNode();
        BigDecimal realQty = line.getRealQty() != null ? line.getRealQty()
                : (line.getMustQty() != null ? line.getMustQty() : BigDecimal.ZERO);
        BigDecimal mustQty = line.getMustQty() != null ? line.getMustQty() : realQty;
        if (realQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("生产入库实收数量必须大于 0: " + line.getMaterialCode());
        }
        String unit = StringUtils.hasText(line.getUnitCode())
                ? line.getUnitCode().trim() : props.getStockInDefaultUnitNumber();
        String workShop = KingdeeSaveEnvelope.firstNonBlank(
                line.getWorkShopCode(), reqWorkShop, props.getPrdInStockWorkShopNumber());
        String inStockType = KingdeeSaveEnvelope.firstNonBlank(line.getInStockType(), props.getPrdInStockInStockType());
        String productType = KingdeeSaveEnvelope.firstNonBlank(line.getProductType(), "1");
        String stock = StringUtils.hasText(line.getWarehouseCode()) ? line.getWarehouseCode().trim() : null;
        if (!StringUtils.hasText(stock)) {
            String fallback = props.getStockInDefaultWarehouseNumber();
            String unassigned = props.getStockInUnassignedWarehouseNumber();
            if (StringUtils.hasText(fallback)
                    && !(StringUtils.hasText(unassigned) && unassigned.equalsIgnoreCase(fallback))) {
                stock = fallback.trim();
            }
        }
        String lot = BatchNoNormalizer.normalize(line.getBatchNo());

        Long morptEntryId = firstNonNull(line.getSrcEntryId());
        Long morptBillId = firstNonNull(line.getSrcInterId());
        KingdeeSaveEnvelope.putLong(entry, "FSrcEntryId", morptEntryId);
        entry.put("FIsNew", false);
        KingdeeSaveEnvelope.putNumberRef(entry, "FMaterialId", line.getMaterialCode());
        entry.put("FCheckProduct", false);
        entry.put("FProductType", productType);
        entry.put("FInStockType", inStockType);
        KingdeeSaveEnvelope.putNumberRef(entry, "FUnitID", unit);
        entry.put("FMustQty", mustQty);
        entry.put("FRealQty", realQty);
        entry.put("FCostRate", 100.0);
        KingdeeSaveEnvelope.putNumberRef(entry, "FBaseUnitId", unit);
        entry.put("FBaseMustQty", mustQty);
        entry.put("FBaseRealQty", realQty);
        entry.put("FOwnerTypeId", props.getStockInOwnerTypeHead());
        KingdeeSaveEnvelope.putNumberRef(entry, "FOwnerId", props.getStockInOrgNumber());
        entry.put("FISBACKFLUSH", line.getBackFlush() == null || Boolean.TRUE.equals(line.getBackFlush()));
        if (StringUtils.hasText(workShop)) {
            KingdeeSaveEnvelope.putNumberRef(entry, "FWorkShopId1", workShop);
        }
        if (StringUtils.hasText(stock)) {
            KingdeeSaveEnvelope.putNumberRef(entry, "FStockId", stock);
        }
        if (StringUtils.hasText(lot)) {
            KingdeeSaveEnvelope.putNumberRef(entry, "FLot", lot);
        }
        KingdeeSaveEnvelope.putNumberRef(entry, "FStockStatusId", props.getStockInStockStatusNumber());
        if (StringUtils.hasText(line.getMoBillNo())) {
            entry.put("FMoBillNo", line.getMoBillNo().trim());
        }
        String srcBillNo = KingdeeSaveEnvelope.firstNonBlank(line.getSrcBillNo(), line.getMoBillNo());
        if (StringUtils.hasText(srcBillNo)) {
            entry.put("FSrcBillNo", srcBillNo.trim());
        }
        KingdeeSaveEnvelope.putLong(entry, "FMoId", line.getMoId());
        KingdeeSaveEnvelope.putLong(entry, "FMoEntryId", line.getMoEntryId());
        if (line.getMoEntrySeq() != null && line.getMoEntrySeq() > 0) {
            entry.put("FMoEntrySeq", line.getMoEntrySeq());
        }
        if (line.getSrcEntrySeq() != null && line.getSrcEntrySeq() > 0) {
            entry.put("FSrcEntrySeq", line.getSrcEntrySeq());
        } else if (line.getMoEntrySeq() != null && line.getMoEntrySeq() > 0) {
            entry.put("FSrcEntrySeq", line.getMoEntrySeq());
        }
        KingdeeSaveEnvelope.putNumberRef(entry, "FStockUnitId", unit);
        entry.put("FStockRealQty", realQty);
        entry.put("FSecRealQty", 0.0);
        entry.put("FSrcBillType", SRC_BILL_TYPE_MORPT);
        KingdeeSaveEnvelope.putLong(entry, "FSrcInterId", morptBillId);
        entry.put("FBasePrdRealQty", realQty);
        entry.put("FIsFinished", false);
        KingdeeSaveEnvelope.putLong(entry, "FMOMAINENTRYID",
                firstNonNull(line.getMoEntryId(), line.getSrcEntryId()));
        entry.put("FKeeperTypeId", "BD_KeeperOrg");
        KingdeeSaveEnvelope.putNumberRef(entry, "FKeeperId", props.getStockInOrgNumber());
        entry.put("FSelReStkQty", 0.0);
        entry.put("FBaseSelReStkQty", 0.0);
        entry.put("FIsOverLegalOrg", false);
        entry.put("FOperNumber", 0);
        KingdeeSaveEnvelope.putNumberRef(entry, "FReportPrdOrgId", props.getStockInOrgNumber());
        entry.put("FSampleQty2", 0.0);
        entry.put("FActualQty", 0.0);
        entry.put("FSumActualQty", 0.0);
        String entryNote = KingdeeSaveEnvelope.firstNonBlank(line.getEntryNote(), line.getMaterialName());
        if (StringUtils.hasText(entryNote)) {
            entry.put("FMemo", entryNote.trim());
        }

        if (morptBillId != null && morptBillId > 0 && morptEntryId != null && morptEntryId > 0) {
            ArrayNode links = entry.putArray("FEntity_Link");
            ObjectNode link = links.addObject();
            link.put("FEntity_Link_FFlowId", blankAsSpace(props.getPrdInStockMorptLinkFlowId()));
            link.put("FEntity_Link_FFlowLineId", props.getPrdInStockMorptLinkFlowLineId());
            link.put("FEntity_Link_FRuleId", props.getPrdInStockMorptLinkRuleId());
            link.put("FEntity_Link_FSTableName", props.getPrdInStockMorptLinkSTableName());
            link.put("FEntity_Link_FSBillId", String.valueOf(morptBillId));
            link.put("FEntity_Link_FSId", String.valueOf(morptEntryId));
            link.put("FEntity_Link_FBasePrdRealQtyOld", realQty);
            link.put("FEntity_Link_FBasePrdRealQty", realQty);
            link.put("FEntity_Link_FBaseQtyOld", realQty);
            link.put("FEntity_Link_FBaseQty", realQty);
        }
        return entry;
    }

    private static String buildDefaultNote(KingdeePrdInStockRequest req) {
        StringBuilder sb = new StringBuilder("WMS PDA生产汇报入库");
        if (req != null && StringUtils.hasText(req.getBatchNo())) {
            sb.append(" 批次").append(req.getBatchNo().trim());
        }
        return sb.toString();
    }

    private static String blankAsSpace(String value) {
        return StringUtils.hasText(value) ? value.trim() : " ";
    }

    private static Long firstNonNull(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long v : values) {
            if (v != null && v > 0) {
                return v;
            }
        }
        return null;
    }

    private static String formatBillDate(LocalDate date) {
        LocalDate d = date != null ? date : LocalDate.now();
        return LocalDateTime.of(d.getYear(), d.getMonth(), d.getDayOfMonth(), 0, 0, 0).format(DATE_TIME);
    }
}
