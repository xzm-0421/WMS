package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

/**
 * 金蝶单据审核（Audit）请求 data 节点。
 */
public final class KingdeeBillAuditPayload {

    private KingdeeBillAuditPayload() {
    }

    public static ObjectNode build(ObjectMapper objectMapper, String billNo, String billId) {
        ObjectNode data = KingdeeBillWorkflowPayload.buildNumbersAndIds(objectMapper, billNo, billId);
        data.put("InterationFlags", "");
        data.put("UseOrgId", 0);
        data.put("NetworkCtrl", false);
        data.put("IsVerifyProcInst", true);
        data.put("IgnoreInterationFlag", true);
        data.put("UseBatControlTimes", false);
        return data;
    }
}
