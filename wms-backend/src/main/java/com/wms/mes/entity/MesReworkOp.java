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
@TableName("mes_rework_op")
public class MesReworkOp extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String defectNo;
    private Integer seqNo;
    private String processCode;
    private String processName;
    private BigDecimal planQty;
    private BigDecimal reportedQty;
    private String opStatus;
}
