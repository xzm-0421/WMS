package com.wms.pdastockcount.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pda_stockcount_line")
public class PdaStockCountLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String billNo;
    private Integer lineNo;
    private Long entryId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String warehouseCode;
    private String locationCode;
    private String batchNo;
    private String unitCode;
    private BigDecimal bookQty;
    private BigDecimal actualQty;
    /** PENDING / COUNTED */
    private String lineStatus;
    private String scannedBarcode;
    private LocalDateTime lastScanTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
