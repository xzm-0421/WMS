package com.wms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * WMS 外部用户与金蝶用户映射。
 * 业务绑定键：kd_user_number（FNumber）；API 调用加速：kd_user_id（FUserID）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_kingdee_map")
public class SysUserKingdeeMap extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 外部系统用户标识（WMS sys_user.id） */
    private String externalUserId;
    /** 金蝶用户编码 FNumber（业务绑定键） */
    private String kdUserNumber;
    /** 金蝶用户 ID FUserID（冗余，供 WorkflowAudit.UserId） */
    private Long kdUserId;
    private String remark;
    private Integer status;
}
