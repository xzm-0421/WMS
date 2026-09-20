package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_route_op")
public class MesRouteOp extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long routeId;
    private String productCode;
    private String versionNo;
    private Integer seqNo;
    private String processCode;
    private String processName;
    private BigDecimal stdHours;
    private Integer inspectFlag;
    private Integer reworkJoinFlag;
    private String workCenterCode;
}
