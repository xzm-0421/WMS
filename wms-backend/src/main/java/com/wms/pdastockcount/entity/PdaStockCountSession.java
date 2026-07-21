package com.wms.pdastockcount.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pda_stockcount_session")
public class PdaStockCountSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String billNo;
    private LocalDate billDate;
    private String warehouseCode;
    private String stockOrgCode;
    private String remark;
    /** COUNTING / COMPLETED */
    private String status;
    private Integer totalLines;
    private Integer countedLines;
    private String deviceNo;
    private String operatorId;
    private String operatorName;
    private LocalDateTime lastScanTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
