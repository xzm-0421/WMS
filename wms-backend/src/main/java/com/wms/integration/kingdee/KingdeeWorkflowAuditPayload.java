package com.wms.integration.kingdee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

/**
 * 金蝶工作流审批（WorkflowAudit）请求 data。
 * ApprovalType：1 通过 / 2 驳回 / 3 终止。
 */
public final class KingdeeWorkflowAuditPayload {

    public static final int APPROVAL_PASS = 1;
    public static final int APPROVAL_REJECT = 2;
    public static final int APPROVAL_TERMINATE = 3;

    private KingdeeWorkflowAuditPayload() {
    }

    public static ObjectNode build(ObjectMapper mapper, String formId, String billNo, String billId,
                                   int approvalType, String disposition) {
        return build(mapper, formId, billNo, billId, approvalType, disposition, null, null);
    }

    /**
     * @param kingdeeUserId 金蝶审批人用户内码（UserId），生产补料/委外补料等审批流必填
     * @param kingdeeUserName 金蝶审批人用户名（可选，UserName）
     */
    public static ObjectNode build(ObjectMapper mapper, String formId, String billNo, String billId,
                                   int approvalType, String disposition,
                                   Long kingdeeUserId, String kingdeeUserName) {
        ObjectNode data = mapper.createObjectNode();
        data.put("FormId", formId != null ? formId.trim() : "");
        // 文档：Ids 为字符串 "Id1,Id2,..."；同时兼容 Numbers 数组
        if (StringUtils.hasText(billId)) {
            data.put("Ids", billId.trim());
        } else {
            data.put("Ids", "");
        }
        ArrayNode numbers = data.putArray("Numbers");
        if (StringUtils.hasText(billNo)) {
            numbers.add(billNo.trim());
        }
        data.put("UserId", kingdeeUserId != null && kingdeeUserId > 0 ? kingdeeUserId : 0L);
        data.put("UserName", StringUtils.hasText(kingdeeUserName) ? kingdeeUserName.trim() : "");
        data.put("ApprovalType", approvalType);
        data.put("ActionResultId", "");
        data.put("PostId", 0);
        data.put("PostNumber", "");
        data.put("Disposition", StringUtils.hasText(disposition) ? disposition.trim() : "WMS PDA 确认");
        return data;
    }
}
