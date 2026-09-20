package com.wms.mes.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mes_runtime")
public class MesRuntime {

    @TableId(type = IdType.INPUT)
    private Integer id;
    private String networkStatus;
    private Integer failStreak;
    private LocalDateTime lastCheckTime;
    private LocalDateTime lastOnlineTime;
    private String lastError;
}
