package com.wms.integration.kingdee.dto;

import lombok.Data;

/**
 * 金蝶 SEC_User 用户简要信息（选择绑定用）。
 */
@Data
public class KingdeeSecUserVo {

    /** FUserID */
    private Long userId;
    /** FName */
    private String userName;
    /** FPhone */
    private String phone;
}
