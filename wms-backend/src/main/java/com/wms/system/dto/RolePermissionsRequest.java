package com.wms.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class RolePermissionsRequest {

    private List<Long> permissionIds;
    private List<String> permissionCodes;
    private Integer dataScope;
    private List<String> warehouseScope;
}
