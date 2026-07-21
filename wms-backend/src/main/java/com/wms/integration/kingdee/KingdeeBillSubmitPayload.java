package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

/**
 * 金蝶单据提交（Submit）请求 data 节点。
 */
public final class KingdeeBillSubmitPayload {

    private KingdeeBillSubmitPayload() {
    }

    public static ObjectNode build(ObjectMapper objectMapper, String billNo, String billId) {
        ObjectNode data = KingdeeBillWorkflowPayload.buildNumbersAndIds(objectMapper, billNo, billId);
        data.put("SelectedPostId", 0);
        data.put("UseOrgId", 0);
        data.put("NetworkCtrl", false);
        data.put("IgnoreInterationFlag", true);
        return data;
    }
}
