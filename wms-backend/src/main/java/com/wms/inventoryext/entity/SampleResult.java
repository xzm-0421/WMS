package com.wms.inventoryext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sample_result")
public class SampleResult {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String planNo;
    private String materialCode;
    private String batchNo;
    private String locationCode;
    private String qualityStatus;
    private LocalDate expireDate;
    private Integer alertFlag;
    private String remark;
    private LocalDateTime checkTime;
}
