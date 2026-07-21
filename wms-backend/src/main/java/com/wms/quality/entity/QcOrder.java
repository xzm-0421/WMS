package com.wms.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("qc_order")
public class QcOrder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String qcNo;
    private String sourceType;
    private String sourceNo;
    private String materialCode;
    private String batchNo;
    private BigDecimal sampleQty;
    private String status;
    private String result;
    private String inspectorId;
    private String inspectorName;
    private LocalDateTime inspectTime;
    private String remark;
    private LocalDateTime createTime;
}
