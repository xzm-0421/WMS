package com.wms.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysRoleDto {

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private Integer dataScope;
    private Integer status;
    private List<Long> permissionIds;
    private List<String> permissionCodes;
    private List<String> warehouseScope;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
