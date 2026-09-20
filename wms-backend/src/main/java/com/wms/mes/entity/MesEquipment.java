package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mes_equipment")
public class MesEquipment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String equipmentCode;
    private String equipmentName;
    private String processCode;
    private String specModel;
    private Integer status;
    private String syncStatus;
    private LocalDateTime lastSyncTime;
    private String failReason;
}
