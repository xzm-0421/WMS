package com.wms.pdareceive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pda_receive_scan_line")
public class PdaReceiveScanLine {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sessionId;
    private String billType;
    private String direction;
    private String billNo;
    private Integer lineNo;
    private String materialCode;
    private String materialName;
    private String specification;
    private String batchNo;
    private BigDecimal planQty;
    private Integer checked;
    private BigDecimal scannedQty;
    private BigDecimal submittedQty;
    private String unitCode;
    private String scannedBarcode;
    /** 金蝶仓库编码（FStockId），与 WMS 仓库区分 */
    private String erpStockCode;
    private LocalDateTime lastScanTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
