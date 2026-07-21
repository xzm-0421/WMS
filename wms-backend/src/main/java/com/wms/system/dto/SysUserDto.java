package com.wms.system.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class SysUserDto {

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String email;
    private Long deptId;
    private Integer status;
    private String warehouseScopeJson;
    private List<Long> roleIds;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
