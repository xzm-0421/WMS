package com.wms.system.dto;

import lombok.Data;

@Data
public class SysPermissionDto {

    private Long id;
    private String permissionCode;
    private String permissionName;
    private String module;
}
