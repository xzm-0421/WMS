package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillLineVo;
import com.wms.integration.kingdee.dto.KingdeeReceiveBillVo;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 解析金蝶 View(PUR_ReceiveBill) 返回的 Result 节点。
 * 明细分录支持 FDetailEntity / PUR_ReceiveEntry 等。
 */
public final class KingdeeReceiveBillViewParser {

    private KingdeeReceiveBillViewParser() {
    }

    public static KingdeeReceiveBillVo parse(JsonNode billNode) {
        if (billNode == null || billNode.isMissingNode() || billNode.isNull()) {
            return null;
        }
        String billNo = firstText(billNode, "FBillNo", "BillNo", "FNumber", "Number");
        if (!StringUtils.hasText(billNo)) {
            return null;
        }
        KingdeeReceiveBillVo bill = KingdeeReceiveBillVo.builder()
                .billNo(billNo.trim())
                .billDate(parseDate(firstText(billNode, "FDate", "Date")))
                .supplierCode(firstNonBlank(
                        refNumber(billNode, "FWorkShopId", "WorkShopId", "FSupplierId", "SupplierId", "FSubSupplierId"),
                        refNumber(billNode, "F_YVZR_Base_qtr")))
                .supplierName(firstNonBlank(
                        refName(billNode, "FWorkShopId", "WorkShopId", "FSupplierId", "SupplierId", "FSubSupplierId"),
                        refName(billNode, "F_YVZR_Base_qtr")))
                .documentStatus(firstText(billNode, "FDocumentStatus", "DocumentStatus"))
                .warehouseCode(refNumber(billNode, "FStockId0", "FStockOrgId", "StockOrgId", "FStockId", "StockId"))
                .billId(parseLong(firstText(billNode, "FID", "Id")))
                .moBillNo(firstNonBlank(
                        firstText(billNode, "FMOBillNO", "FMoBillNo", "MoBillNo"),
                        firstText(billNode, "FSubReqBillNo", "SubReqBillNo")))
                .parentMaterialCode(refNumber(billNode, "FMaterialId", "MaterialId", "FParentMaterialId"))
                .build();
        List<KingdeeReceiveBillLineVo> lines = parseMaterialLines(billNode);
        for (KingdeeReceiveBillLineVo line : lines) {
            if (!StringUtils.hasText(line.getPpBomBillNo())) {
                line.setPpBomBillNo(bill.getBillNo());
            }
            if (line.getPpBomEntryId() == null) {
                line.setPpBomEntryId(line.getEntryId());
            }
            if (!StringUtils.hasText(line.getMoBillNo())) {
                line.setMoBillNo(bill.getMoBillNo());
            }
            if (!StringUtils.hasText(line.getSubReqBillNo())) {
                line.setSubReqBillNo(bill.getMoBillNo());
            }
            if (!StringUtils.hasText(line.getParentMaterialCode())) {
                line.setParentMaterialCode(bill.getParentMaterialCode());
            }
        }
        bill.setLines(lines);
        bill.setTotalLines(lines.size());
        return bill;
    }

    public static List<KingdeeReceiveBillLineVo> parseMaterialLines(JsonNode billNode) {
        if (billNode == null || billNode.isMissingNode() || billNode.isNull()) {
            return List.of();
        }
        return parseLines(billNode);
    }

    private static List<KingdeeReceiveBillLineVo> parseLines(JsonNode billNode) {
        JsonNode entries = findEntries(billNode);
        if (entries == null || !entries.isArray()) {
            return List.of();
        }
        List<KingdeeReceiveBillLineVo> lines = new ArrayList<>();
        int idx = 0;
        for (JsonNode entry : entries) {
            idx++;
            KingdeeReceiveBillLineVo line = mapEntryLine(entry, idx);
            if (line != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static KingdeeReceiveBillLineVo mapEntryLine(JsonNode entry, int fallbackSeq) {
        String materialCode = refNumber(entry,
                "FMaterialId", "FMaterialID", "MaterialId", "MaterialID");
        if (!StringUtils.hasText(materialCode)) {
            return null;
        }
        String materialName = refName(entry, "FMaterialId", "FMaterialID", "MaterialId", "MaterialID");
        String materialDesc = resolveMaterialDesc(entry);
        if (!StringUtils.hasText(materialDesc)) {
            materialDesc = materialName;
        }
        String specification = resolveSpecification(entry);
        int seq = parseInt(firstText(entry, "FSeq", "Seq", "FDetailEntity_FSeq"), fallbackSeq);
        String batchNo = firstNonBlank(
                text(entry, "FLot_Text", "Lot_Text"),
                refNumber(entry, "FLot", "Lot", "FLotId", "LotId"));
        String stockUnit = firstNonBlank(
                refNumber(entry, "FUnitId", "UnitId", "FUnitID", "UnitID"),
                refNumber(entry, "FBaseUnitId", "BaseUnitId", "FBaseUnitID", "BaseUnitID"));
        String stockWarehouse = refNumber(entry, "FStockId", "StockId", "FStockID", "StockID");
        BigDecimal actReceiveQty = parseDecimal(firstText(entry,
                "FActReceiveQty", "ActReceiveQty",
                "FActlandQty", "ActlandQty",
                "FActLandQty", "ActLandQty",
                "FNoPickedQty", "NoPickedQty",
                "FMustQty", "MustQty",
                "FAppQty", "AppQty"));
        BigDecimal mustQty = parseDecimal(firstText(entry, "FMustQty", "MustQty"));
        BigDecimal pickedQty = parseDecimal(firstText(entry, "FPickedQty", "PickedQty", "FActualQty", "ActualQty"));
        BigDecimal noPickedQty = parseDecimal(firstText(entry, "FNoPickedQty", "NoPickedQty"));
        if (noPickedQty.compareTo(BigDecimal.ZERO) > 0) {
            actReceiveQty = noPickedQty;
        } else if (mustQty.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remain = mustQty.subtract(pickedQty);
            if (remain.compareTo(BigDecimal.ZERO) > 0) {
                actReceiveQty = remain;
            } else if (actReceiveQty.compareTo(BigDecimal.ZERO) <= 0) {
                actReceiveQty = mustQty;
            }
        }
        BigDecimal qualifiedQty = parseDecimal(firstText(entry,
                "FReceiveBaseQty", "ReceiveBaseQty"));
        BigDecimal stockBaseQty = parseDecimal(firstText(entry,
                "FStockBaseQty", "StockBaseQty"));
        BigDecimal baseUnitQty = parseDecimal(firstText(entry,
                "FBaseUnitQty", "BaseUnitQty", "FBaseMustQty", "BaseMustQty"));
        BigDecimal inStockJoinBaseQty = parseDecimal(firstText(entry,
                "FInStockJoinBaseQty", "InStockJoinBaseQty", "INSTOCKJOINBASEQTY"));
        BigDecimal receiveBase = qualifiedQty.compareTo(BigDecimal.ZERO) > 0 ? qualifiedQty : stockBaseQty;
        if (receiveBase.compareTo(BigDecimal.ZERO) <= 0) {
            receiveBase = baseUnitQty.compareTo(BigDecimal.ZERO) > 0 ? baseUnitQty : actReceiveQty;
        }
        BigDecimal remainInStockBaseQty = receiveBase.subtract(inStockJoinBaseQty);
        if (remainInStockBaseQty.compareTo(BigDecimal.ZERO) < 0) {
            remainInStockBaseQty = BigDecimal.ZERO;
        }
        String srcFormId = firstText(entry, "SrcFormId", "FSrcFormId");
        String srcBillNo = firstText(entry, "SrcBillNo", "FSrcBillNo");
        String orderBillNo = firstText(entry, "FOrderBillNo", "OrderBillNo");
        Long poOrderEntryId = parseLong(firstText(entry,
                "POORDERENTRYID", "FPOORDERENTRYID", "FPOOrderEntryId", "PoOrderEntryId"));
        String poOrderNo = resolvePoOrderNo(srcFormId, srcBillNo, orderBillNo, poOrderEntryId);
        Long entryId = parseLong(firstText(entry, "FEntryID", "EntryID", "Id", "FPPBomEntryId"));
        return KingdeeReceiveBillLineVo.builder()
                .lineNo(seq > 0 ? seq : fallbackSeq)
                .materialCode(materialCode)
                .materialName(materialName)
                .materialDesc(materialDesc)
                .specification(specification)
                .batchNo(batchNo)
                .unitCode(stockUnit)
                .stockWarehouseCode(stockWarehouse)
                .entryId(entryId)
                .planQty(actReceiveQty)
                .qualifiedQty(qualifiedQty)
                .stockBaseQty(stockBaseQty)
                .baseUnitQty(baseUnitQty.compareTo(BigDecimal.ZERO) > 0 ? baseUnitQty : receiveBase)
                .inStockJoinBaseQty(inStockJoinBaseQty)
                .remainInStockBaseQty(remainInStockBaseQty)
                .poOrderNo(poOrderNo)
                .poOrderEntryId(poOrderEntryId)
                .moBillNo(firstText(entry, "FMoBillNo", "MoBillNo", "FMOBillNO"))
                .moId(parseLong(firstText(entry, "FMoId", "MoId")))
                .moEntryId(parseLong(firstText(entry, "FMoEntryId", "MoEntryId")))
                .moEntrySeq(parseInt(firstText(entry, "FMoEntrySeq", "MoEntrySeq"), 0) > 0
                        ? parseInt(firstText(entry, "FMoEntrySeq", "MoEntrySeq"), 0) : null)
                .ppBomEntryId(parseLong(firstNonBlank(
                        firstText(entry, "FPPBomEntryId", "PPBomEntryId", "FPPbomEntryId"),
                        entryId == null ? null : String.valueOf(entryId))))
                .ppBomBillNo(firstText(entry, "FPPBomBillNo", "PPBomBillNo", "FPPbomBillNo"))
                .parentMaterialCode(refNumber(entry, "FParentMaterialId", "ParentMaterialId"))
                .subReqBillNo(firstText(entry, "FSubReqBillNo", "SubReqBillNo", "FSubReqBillNO"))
                .subReqId(parseLong(firstText(entry, "FSubReqId", "SubReqId")))
                .subReqEntryId(parseLong(firstText(entry, "FSubReqEntryId", "SubReqEntryId")))
                .subReqEntrySeq(parseInt(firstText(entry, "FSubReqEntrySeq", "SubReqEntrySeq"), 0) > 0
                        ? parseInt(firstText(entry, "FSubReqEntrySeq", "SubReqEntrySeq"), 0) : null)
                .receivedQty(BigDecimal.ZERO)
                .build();
    }

    private static String resolvePoOrderNo(String srcFormId, String srcBillNo,
                                           String orderBillNo, Long poOrderEntryId) {
        if (StringUtils.hasText(srcBillNo)
                && ("PUR_PurchaseOrder".equalsIgnoreCase(srcFormId)
                || (poOrderEntryId != null && poOrderEntryId > 0))) {
            return srcBillNo.trim();
        }
        if (StringUtils.hasText(orderBillNo)) {
            return orderBillNo.trim();
        }
        return "";
    }

    private static String resolveMaterialDesc(JsonNode entry) {
        String direct = firstText(entry, "FMaterialDesc", "MaterialDesc", "FDescription", "Description");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        JsonNode descNode = entry.get("FMaterialDesc");
        if (descNode == null) {
            descNode = entry.get("MaterialDesc");
        }
        return localizedArrayText(descNode);
    }

    private static String resolveSpecification(JsonNode entry) {
        String direct = firstText(entry, "FMateriaModel", "MateriaModel", "FSpecification", "Specification");
        if (StringUtils.hasText(direct)) {
            return direct;
        }
        for (String materialField : new String[]{"FMaterialId", "FMaterialID", "MaterialId", "MaterialID"}) {
            JsonNode material = entry.get(materialField);
            if (material != null && material.isObject()) {
                String spec = localizedArrayText(material.get("FSpecification"));
                if (!StringUtils.hasText(spec)) {
                    spec = localizedArrayText(material.get("Specification"));
                }
                if (StringUtils.hasText(spec)) {
                    return spec;
                }
                spec = text(material, "FSpecification", "Specification");
                if (StringUtils.hasText(spec)) {
                    return spec;
                }
            }
        }
        return "";
    }

    private static JsonNode findEntries(JsonNode billNode) {
        String[] names = {
                "PUR_ReceiveEntry", "FDetailEntity", "DetailEntity",
                "FReceiveEntry", "ReceiveEntry", "FReceiveBillEntry", "ReceiveBillEntry",
                "FEntity", "Entity"
        };
        for (String name : names) {
            JsonNode node = billNode.get(name);
            if (node != null && node.isArray() && !node.isEmpty()) {
                return node;
            }
        }
        Iterator<String> it = billNode.fieldNames();
        while (it.hasNext()) {
            String field = it.next();
            if (field.toLowerCase().contains("receiveentry")
                    || field.toLowerCase().contains("entry")
                    || field.toLowerCase().contains("entity")) {
                JsonNode node = billNode.get(field);
                if (node != null && node.isArray() && !node.isEmpty()) {
                    return node;
                }
            }
        }
        return null;
    }

    private static String localizedArrayText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("").trim();
        }
        if (node.isArray() && !node.isEmpty()) {
            for (JsonNode item : node) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                String value = text(item, "Value", "Name", "FName");
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
            JsonNode first = node.get(0);
            if (first != null && first.isObject()) {
                return text(first, "Value", "Name", "FName");
            }
        }
        if (node.isObject()) {
            return text(node, "Value", "Name", "FName");
        }
        return "";
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull()) {
                if (child.isObject()) {
                    String n = text(child, "FNumber", "Number", "Name", "FName");
                    if (StringUtils.hasText(n)) {
                        return n;
                    }
                    String localized = localizedArrayText(child.get("Name"));
                    if (StringUtils.hasText(localized)) {
                        return localized;
                    }
                } else if (child.isArray()) {
                    String localized = localizedArrayText(child);
                    if (StringUtils.hasText(localized)) {
                        return localized;
                    }
                } else {
                    String v = child.asText("");
                    if (StringUtils.hasText(v)) {
                        return v.trim();
                    }
                }
            }
        }
        return "";
    }

    private static String refNumber(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode ref = node.get(name);
            if (ref != null && ref.isObject()) {
                String n = text(ref, "FNumber", "Number");
                if (StringUtils.hasText(n)) {
                    return n;
                }
            }
        }
        return "";
    }

    private static String refName(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode ref = node.get(name);
            if (ref != null && ref.isObject()) {
                String localized = localizedArrayText(ref.get("FName"));
                if (!StringUtils.hasText(localized)) {
                    localized = localizedArrayText(ref.get("Name"));
                }
                if (StringUtils.hasText(localized)) {
                    return localized;
                }
                localized = localizedArrayText(ref.get("MultiLanguageText"));
                if (StringUtils.hasText(localized)) {
                    return localized;
                }
                String n = text(ref, "FName", "Name");
                if (StringUtils.hasText(n)) {
                    return n;
                }
            }
        }
        return "";
    }

    private static String text(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return "";
        }
        for (String name : names) {
            if (node.has(name) && !node.get(name).isNull()) {
                String v = node.get(name).asText("");
                if (StringUtils.hasText(v)) {
                    return v.trim();
                }
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v.trim();
            }
        }
        return "";
    }

    private static LocalDate parseDate(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            String d = s.length() >= 10 ? s.substring(0, 10) : s;
            return LocalDate.parse(d);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseDecimal(String s) {
        try {
            return new BigDecimal(s == null || s.isBlank() ? "0" : s.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            if (!StringUtils.hasText(s)) {
                return fallback;
            }
            return Integer.parseInt(s.trim().split("\\.")[0]);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Long parseLong(String s) {
        try {
            if (!StringUtils.hasText(s)) {
                return null;
            }
            return Long.parseLong(s.trim().split("\\.")[0]);
        } catch (Exception e) {
            return null;
        }
    }
}
