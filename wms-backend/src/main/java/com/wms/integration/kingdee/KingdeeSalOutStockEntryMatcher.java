package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 销售出库 View 分录与 PDA 扫码行匹配，并判断批号/仓库是否已由下推带出。
 */
public final class KingdeeSalOutStockEntryMatcher {

    private KingdeeSalOutStockEntryMatcher() {
    }

    public static List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> match(
            JsonNode billNode, List<KingdeeSalOutStockEntryFill> fills) {
        JsonNode entries = findEntries(billNode);
        List<KingdeeSalOutStockLotStockBuilder.EntryUpdate> updates = new ArrayList<>();
        if (entries == null || fills == null || fills.isEmpty()) {
            return updates;
        }
        List<KingdeeSalOutStockEntryFill> pending = new ArrayList<>();
        for (KingdeeSalOutStockEntryFill fill : fills) {
            if (fill != null) {
                pending.add(fill);
            }
        }
        for (JsonNode entry : entries) {
            Long entryId = parseEntryId(entry);
            if (entryId == null || entryId <= 0) {
                continue;
            }
            String material = extractMaterial(entry);
            Long srcEntryId = extractLinkSourceEntryId(entry);
            Integer seq = parseIntQuiet(nodeText(entry, "FSeq", "Seq", "FSEQ"));
            KingdeeSalOutStockEntryFill matched = takeMatch(pending, srcEntryId, material, seq);
            if (matched == null) {
                continue;
            }
            updates.add(toUpdate(entryId, matched));
        }
        if (!pending.isEmpty()) {
            for (JsonNode entry : entries) {
                if (pending.isEmpty()) {
                    break;
                }
                Long entryId = parseEntryId(entry);
                if (entryId == null || entryId <= 0) {
                    continue;
                }
                boolean already = updates.stream().anyMatch(u -> entryId.equals(u.entryId()));
                if (already) {
                    continue;
                }
                String material = extractMaterial(entry);
                for (int i = 0; i < pending.size(); i++) {
                    KingdeeSalOutStockEntryFill fill = pending.get(i);
                    if (sameMaterial(fill, material)) {
                        updates.add(toUpdate(entryId, fill));
                        pending.remove(i);
                        break;
                    }
                }
            }
        }
        // 物料编码/源分录都对不上时，按分录顺序一一对应（下推通常保持行序）
        if (updates.isEmpty()) {
            List<KingdeeSalOutStockEntryFill> remain = new ArrayList<>();
            for (KingdeeSalOutStockEntryFill fill : fills) {
                if (fill != null) {
                    remain.add(fill);
                }
            }
            int i = 0;
            for (JsonNode entry : entries) {
                if (i >= remain.size()) {
                    break;
                }
                Long entryId = parseEntryId(entry);
                if (entryId == null || entryId <= 0) {
                    continue;
                }
                updates.add(toUpdate(entryId, remain.get(i)));
                i++;
            }
        }
        return updates;
    }

    /**
     * 下推已带出批号/仓库（含 View 只返回内码、界面能显示名称的情况）。
     */
    public static boolean alreadyHasLotAndStock(JsonNode billNode) {
        JsonNode entries = findEntries(billNode);
        if (entries == null) {
            return false;
        }
        int checked = 0;
        for (JsonNode entry : entries) {
            if (entry == null || entry.isNull()) {
                continue;
            }
            if (parseEntryId(entry) == null) {
                continue;
            }
            checked++;
            if (!hasBaseDataValue(entry, "FLot", "Lot") && !StringUtils.hasText(nodeText(entry, "FLot_Text", "Lot_Text"))) {
                return false;
            }
            if (!hasBaseDataValue(entry, "FStockId", "FStockID", "StockId", "StockID")) {
                return false;
            }
        }
        return checked > 0;
    }

    public static String describeEntries(JsonNode billNode) {
        JsonNode entries = findEntries(billNode);
        if (entries == null) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (JsonNode entry : entries) {
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append("entryId=").append(parseEntryId(entry))
                    .append(",material=").append(extractMaterial(entry))
                    .append(",srcEntryId=").append(extractLinkSourceEntryId(entry))
                    .append(",lot=").append(extractRefNumber(entry, "FLot", "Lot"))
                    .append(",stock=").append(extractRefNumber(entry, "FStockId", "FStockID", "StockId", "StockID"));
        }
        return sb.append("]").toString();
    }

    public static String describeFills(List<KingdeeSalOutStockEntryFill> fills) {
        if (fills == null || fills.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (KingdeeSalOutStockEntryFill fill : fills) {
            if (fill == null) {
                continue;
            }
            if (sb.length() > 1) {
                sb.append(", ");
            }
            sb.append("material=").append(fill.getMaterialCode())
                    .append(",srcEntryId=").append(fill.getSourceEntryId())
                    .append(",lineNo=").append(fill.getLineNo())
                    .append(",lot=").append(fill.getLotNumber())
                    .append(",stock=").append(fill.getStockNumber());
        }
        return sb.append("]").toString();
    }

    static JsonNode findEntries(JsonNode billNode) {
        if (billNode == null || billNode.isNull()) {
            return null;
        }
        for (String name : new String[]{"FEntity", "Entity", "FSaleOrderEntry", "SAL_OUTSTOCKENTRY"}) {
            JsonNode node = billNode.get(name);
            if (node != null && node.isArray() && !node.isEmpty()) {
                return node;
            }
        }
        Iterator<String> it = billNode.fieldNames();
        while (it.hasNext()) {
            String field = it.next();
            String lower = field.toLowerCase();
            if (lower.contains("entity") || lower.contains("entry")) {
                JsonNode node = billNode.get(field);
                if (node != null && node.isArray() && !node.isEmpty()) {
                    return node;
                }
            }
        }
        return null;
    }

    static Long parseEntryId(JsonNode entry) {
        if (entry == null) {
            return null;
        }
        return parseLongQuiet(nodeText(entry, "FENTRYID", "FEntryID", "FEntryId", "EntryID", "Id"));
    }

    static String extractMaterial(JsonNode entry) {
        return extractRefNumber(entry, "FMaterialID", "FMaterialId", "FMATERIALID", "MaterialID", "MaterialId");
    }

    static Long extractLinkSourceEntryId(JsonNode entry) {
        if (entry == null) {
            return null;
        }
        Long direct = parseLongQuiet(nodeText(entry,
                "FSrcId", "FSrcEntryId", "FSOEntryId", "FDeliveryNoticeEntryId", "SourceEntryId"));
        if (direct != null && direct > 0) {
            return direct;
        }
        Iterator<String> fields = entry.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!field.toLowerCase().contains("link")) {
                continue;
            }
            JsonNode links = entry.get(field);
            if (links == null || !links.isArray() || links.isEmpty()) {
                continue;
            }
            for (JsonNode link : links) {
                Long id = parseLongQuiet(nodeText(link,
                        "FEntity_Link_FSId", "FEntity_Link_FSID", "FSId", "FSID",
                        "FLinkSId", "SourceEntryId", "FSIdId"));
                if (id != null && id > 0) {
                    return id;
                }
            }
        }
        return null;
    }

    private static KingdeeSalOutStockEntryFill takeMatch(List<KingdeeSalOutStockEntryFill> pending,
                                                         Long srcEntryId, String material, Integer seq) {
        for (int i = 0; i < pending.size(); i++) {
            KingdeeSalOutStockEntryFill fill = pending.get(i);
            boolean bySrc = fill.getSourceEntryId() != null && fill.getSourceEntryId() > 0
                    && srcEntryId != null && fill.getSourceEntryId().equals(srcEntryId);
            boolean byMaterial = sameMaterial(fill, material);
            boolean bySeq = seq != null && seq > 0 && fill.getLineNo() != null && seq.equals(fill.getLineNo());
            if (bySrc || byMaterial || bySeq) {
                pending.remove(i);
                return fill;
            }
        }
        return null;
    }

    private static boolean sameMaterial(KingdeeSalOutStockEntryFill fill, String material) {
        return fill != null
                && StringUtils.hasText(fill.getMaterialCode())
                && StringUtils.hasText(material)
                && fill.getMaterialCode().trim().equalsIgnoreCase(material.trim());
    }

    private static KingdeeSalOutStockLotStockBuilder.EntryUpdate toUpdate(
            Long entryId, KingdeeSalOutStockEntryFill fill) {
        return new KingdeeSalOutStockLotStockBuilder.EntryUpdate(
                entryId, fill.getLotNumber(), fill.getStockNumber(), fill.getRealQty());
    }

    private static boolean hasBaseDataValue(JsonNode parent, String... names) {
        if (StringUtils.hasText(extractRefNumber(parent, names))) {
            return true;
        }
        if (parent == null || names == null) {
            return false;
        }
        for (String name : names) {
            JsonNode ref = parent.get(name);
            if (ref == null || ref.isNull()) {
                continue;
            }
            if (ref.isTextual() || ref.isNumber()) {
                if (StringUtils.hasText(ref.asText()) && !"0".equals(ref.asText().trim())) {
                    return true;
                }
            }
            if (ref.isObject()) {
                String id = nodeText(ref, "Id", "ID", "msterID", "FItemId", "FSTOCKID");
                if (StringUtils.hasText(id) && !"0".equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String extractRefNumber(JsonNode parent, String... names) {
        if (parent == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode ref = parent.get(name);
            if (ref == null || ref.isNull()) {
                continue;
            }
            if (ref.isTextual() || ref.isNumber()) {
                String t = ref.asText();
                if (StringUtils.hasText(t) && !t.startsWith("{")) {
                    return t.trim();
                }
            }
            String num = nodeText(ref, "FNumber", "FNUMBER", "Number", "FName", "Name");
            if (StringUtils.hasText(num)) {
                return num.trim();
            }
        }
        return null;
    }

    static String nodeText(JsonNode node, String... names) {
        if (node == null || names == null) {
            return null;
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child == null || child.isNull() || child.isMissingNode() || child.isObject() || child.isArray()) {
                continue;
            }
            String text = child.isNumber() ? String.valueOf(child.asLong()) : child.asText();
            if (StringUtils.hasText(text) && !"null".equalsIgnoreCase(text)) {
                return text.trim();
            }
        }
        return null;
    }

    static Long parseLongQuiet(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Long.parseLong(text.trim().split("\\.")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseIntQuiet(String text) {
        Long n = parseLongQuiet(text);
        return n == null ? null : n.intValue();
    }
}
