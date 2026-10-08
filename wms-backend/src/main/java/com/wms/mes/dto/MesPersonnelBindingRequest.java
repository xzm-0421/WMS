package com.wms.mes.dto;

import lombok.Data;

/**
 * 生产人员绑定系统用户（本地字段）。
 */
@Data
public class MesPersonnelBindingRequest {
    private Long sysUserId;
    private String sysUsername;
}
