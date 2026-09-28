package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_defect")
public class MesDefect extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String defectNo;
    private String moNo;
    private String sourceProcessCode;
    private String sourceProcessName;
    private BigDecimal defectQty;
    private String defectType;
    private String defectDesc;
    private String ownerName;
    private String reworkStatus;

    /** 不良单 ERP 同步状态（来自 mes_erp_outbox，仅展示） */
    @TableField(exist = false)
    private String defectSyncStatus;

    /** 返工工单 ERP 同步状态（来自 mes_erp_outbox，仅展示） */
    @TableField(exist = false)
    private String reworkSyncStatus;
}
