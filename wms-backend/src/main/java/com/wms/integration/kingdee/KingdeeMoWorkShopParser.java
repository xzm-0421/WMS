package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.StringUtils;

import java.util.Iterator;

/**
 * 从生产订单 PRD_MO View 结果解析分录生产车间编码。
 * 金蝶生产入库校验：入库明细 FWorkShopId1 必须与对应 MO 行车间一致。
 */
public final class KingdeeMoWorkShopParser {

    private KingdeeMoWorkShopParser() {
    }

    public static String resolve(JsonNode billNode, Long moEntryId, Integer moEntrySeq) {
        return resolveField(billNode, moEntryId, moEntrySeq,
                new String[]{"FWorkShopID", "FWorkShopId1", "FWorkShopId", "WorkShopID", "WorkShopId1", "WorkShopId"},
                new String[]{"FWorkShopId", "WorkShopId", "FWorkShopID", "WorkShopID"});
    }

    /**
     * 解析生产订单行仓库编码（计划仓 / 入库仓），供生产入库自动分配。
     */
    public static String resolveStock(JsonNode billNode, Long moEntryId, Integer moEntrySeq) {
        return resolveField(billNode, moEntryId, moEntrySeq,
                new String[]{"FStockId", "FStockID", "StockId", "StockID", "FStockId0"},
                new String[]{"FStockId0", "FStockId", "StockId", "FStockID"});
    }

    private static String resolveField(JsonNode billNode, Long moEntryId, Integer moEntrySeq,
                                       String[] entryFields, String[] headerFields) {
        if (billNode == null || billNode.isNull() || billNode.isMissingNode()) {
            return null;
        }
        String header = refNumber(billNode, headerFields);
        JsonNode entries = findTreeEntries(billNode);
        if (entries == null) {
            return blankToNull(header);
        }
        JsonNode matched = findEntry(entries, moEntryId, moEntrySeq);
        if (matched != null) {
            String lineValue = refNumber(matched, entryFields);
            if (StringUtils.hasText(lineValue)) {
                return lineValue.trim();
            }
        }
        return blankToNull(header);
    }

    private static JsonNode findEntry(JsonNode entries, Long moEntryId, Integer moEntrySeq) {
        JsonNode byId = null;
        JsonNode bySeq = null;
        JsonNode first = null;
        for (JsonNode entry : entries) {
            if (entry == null || !entry.isObject()) {
                continue;
            }
            if (first == null) {
                first = entry;
            }
            Long entryId = parseLong(firstText(entry, "FEntryID", "EntryID", "Id", "FID"));
            if (moEntryId != null && moEntryId > 0 && moEntryId.equals(entryId)) {
                byId = entry;
                break;
            }
            int seq = parseInt(firstText(entry, "FSeq", "Seq"));
            if (moEntrySeq != null && moEntrySeq > 0 && seq == moEntrySeq && bySeq == null) {
                bySeq = entry;
            }
        }
        if (byId != null) {
            return byId;
        }
        if (bySeq != null) {
            return bySeq;
        }
        return first;
    }

    private static JsonNode findTreeEntries(JsonNode billNode) {
        String[] names = {"FTreeEntity", "TreeEntity", "FEntity", "Entity"};
        for (String name : names) {
            JsonNode node = billNode.get(name);
            if (node != null && node.isArray() && !node.isEmpty()) {
                return node;
            }
        }
        Iterator<String> it = billNode.fieldNames();
        while (it.hasNext()) {
            String field = it.next();
            String lower = field.toLowerCase();
            if (lower.contains("treeentity") || "fentity".equals(lower) || "entity".equals(lower)) {
                JsonNode node = billNode.get(field);
                if (node != null && node.isArray() && !node.isEmpty()) {
                    return node;
                }
            }
        }
        return null;
    }

    private static String refNumber(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode ref = node.get(name);
            if (ref == null || ref.isNull()) {
                continue;
            }
            if (ref.isObject()) {
                String n = firstText(ref, "FNumber", "Number");
                if (StringUtils.hasText(n)) {
                    return n;
                }
            } else if (ref.isTextual() && StringUtils.hasText(ref.asText())) {
                return ref.asText().trim();
            }
        }
        return "";
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null || node.isNull()) {
            return "";
        }
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull() && StringUtils.hasText(child.asText())) {
                return child.asText().trim();
            }
        }
        return "";
    }

    private static Long parseLong(String s) {
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Long.parseLong(s.trim().split("\\.")[0]);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseInt(String s) {
        if (!StringUtils.hasText(s)) {
            return 0;
        }
        try {
            return Integer.parseInt(s.trim().split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
