package com.wms.stockcheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stockcheck_task")
public class StockcheckTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private String planNo;
    private String warehouseCode;
    private String locationCode;
    private String assigneeId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime completeTime;
}
