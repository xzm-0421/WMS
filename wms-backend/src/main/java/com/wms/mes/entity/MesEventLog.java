package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * MES 数据埋点事件（需求 §5.2 的 8 个事件）。
 */
@Data
@TableName("mes_event_log")
public class MesEventLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventName;
    private String bizType;
    private String bizNo;
    private String operatorId;
    private String operatorName;
    private String properties;
    private LocalDateTime eventTime;
    private LocalDateTime createTime;
}
