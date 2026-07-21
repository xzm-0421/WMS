package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

/**
 * 金蝶 Submit / Audit 等按单号操作的公共 data 字段。
 */
final class KingdeeBillWorkflowPayload {

    private KingdeeBillWorkflowPayload() {
    }

    static ObjectNode buildNumbersAndIds(ObjectMapper objectMapper, String billNo, String billId) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("CreateOrgId", 0);
        ArrayNode numbers = data.putArray("Numbers");
        if (StringUtils.hasText(billNo)) {
            numbers.add(billNo.trim());
        }
        data.put("Ids", StringUtils.hasText(billId) ? billId.trim() : "");
        return data;
    }
}
