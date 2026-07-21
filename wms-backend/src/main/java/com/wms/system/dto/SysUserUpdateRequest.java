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
    private List<Long> roleIds;
}
