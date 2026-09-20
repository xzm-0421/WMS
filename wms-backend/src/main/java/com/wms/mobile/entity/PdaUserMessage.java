package com.wms.mobile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDA 用户消息（按业务单据推送给指定用户）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pda_user_message")
public class PdaUserMessage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** WMS 用户 Id */
    private String userId;
    /** TASK / SYSTEM */
    private String messageType;
    private String title;
    private String content;
    /** 业务类型，如 PRODUCTION_ISSUE */
    private String bizType;
    /** 业务单号 */
    private String bizNo;
    /** 0 未读 / 1 已读 */
    private Integer isRead;
}
