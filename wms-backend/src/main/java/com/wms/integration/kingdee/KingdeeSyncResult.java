package com.wms.integration.kingdee;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KingdeeSyncResult {

    private boolean success;
    private String billNo;
    /** 是否已完成金蝶提交 */
    private boolean submitted;
    /** 是否已完成金蝶审核 */
    private boolean audited;
    private String message;
    private String requestJson;
    private String responseJson;
    private String submitRequestJson;
    private String submitResponseJson;
    private String auditRequestJson;
    private String auditResponseJson;
}
