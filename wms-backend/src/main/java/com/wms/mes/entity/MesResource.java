package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_resource")
public class MesResource extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 金蝶内码 FID */
    private Long erpId;
    private String resourceCode;
    private String resourceName;
    /** 金蝶资源类别原值 */
    private String resourceTypeCode;
    /** 映射类别：EQUIPMENT / TEAM / PERSONNEL / OTHER */
    private String resourceType;
    private String workCenterCode;
    private String workCenterName;
    private BigDecimal capacity;
    private String unitCode;
    /** 关联对象类型：EQUIPMENT / PERSONNEL */
    private String refType;
    private String refCode;
    private String refName;
    private Integer status;
    private String syncStatus;
    private LocalDateTime lastSyncTime;
    private String failReason;
}
