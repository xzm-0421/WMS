package com.wms.picking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("workshop_return")
public class WorkshopReturn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private String issueNo;
    private String warehouseCode;
    private String returnReason;
    private String status;
    private String operatorName;
    private LocalDateTime createTime;
    private Integer deleted;
}
