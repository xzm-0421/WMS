package com.wms.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统告警（队列满 / 5 分钟 SLA / 长期未同步）。
 */
@Data
@TableName("sys_alert")
public class SysAlert {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String alertType;
    private String level;
    private String title;
    private String content;
    private String bizNo;
    /** OPEN / ACK / CLOSED */
    private String status;
    private Integer alertCount;
    private LocalDateTime firstTime;
    private LocalDateTime lastTime;
    private String ackBy;
    private LocalDateTime ackTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
