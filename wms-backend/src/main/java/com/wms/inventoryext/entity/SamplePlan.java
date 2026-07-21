package com.wms.inventoryext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sample_plan")
public class SamplePlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planNo;
    private String warehouseCode;
    private LocalDate planDate;
    private String status;
    private String creatorName;
    private LocalDateTime createTime;
    private Integer deleted;
}
