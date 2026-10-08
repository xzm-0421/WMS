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
@TableName("mes_personnel")
public class MesPersonnel extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 金蝶内码 FID */
    private Long erpId;
    private String personnelCode;
    private String personnelName;
    private String deptCode;
    private String deptName;
    private String postCode;
    private String postName;
    private String skillLevel;
    private String workCenterCode;
    private Integer productionFlag;
    /** 本地绑定系统用户（本地可编辑，同步不覆盖） */
    private Long sysUserId;
    private String sysUsername;
    private Integer status;
    private String syncStatus;
    private LocalDateTime lastSyncTime;
    private String failReason;
}
