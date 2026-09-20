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
@TableName("mes_route")
public class MesRoute extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String productCode;
    private String productName;
    private String routeCode;
    private String routeName;
    private String versionNo;
    private BigDecimal overReceiveRatio;
    private Integer status;
    private String syncStatus;
    private LocalDateTime lastSyncTime;
    private String failReason;
}
