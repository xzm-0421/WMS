package com.wms.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class SysUserUpdateRequest {

    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Integer status;
    private String warehouseScopeJson;
    /** 金蝶用户名称 FName */
    private String kdUserNumber;
    /** 金蝶用户 Id FUserID */
    private Long kdUserId;
    private List<Long> roleIds;
}
