package com.wms.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class PermissionModuleGroup {

    private String module;
    private String moduleName;
    private List<SysPermissionDto> permissions;
}
