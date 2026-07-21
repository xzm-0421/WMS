package com.wms.stockcheck.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stockcheck_plan")
public class StockcheckPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planNo;
    private String planName;
    private String warehouseCode;
    private String planType;
    private LocalDate planDate;
    private String status;
    private String creatorId;
    private String creatorName;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
