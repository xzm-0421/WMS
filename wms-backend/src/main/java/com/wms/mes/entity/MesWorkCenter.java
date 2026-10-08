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
@TableName("mes_work_center")
public class MesWorkCenter extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 金蝶内码 FID */
    private Long erpId;
    private String workCenterCode;
    private String workCenterName;
    private String workShopCode;
    private String workShopName;
    private String deptCode;
    private String deptName;
    private BigDecimal capacity;
    private String calendarCode;
    private String calendarName;
    private Integer status;
    private String syncStatus;
    private LocalDateTime lastSyncTime;
    private String failReason;
}
