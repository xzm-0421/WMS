package com.wms.barcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("barcode_archive")
public class BarcodeArchive {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String archiveNo;
    private String barcodeContent;
    private Long barcodeInstanceId;
    private String ruleCode;
    private Integer versionNo;
    private String materialCode;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private String barcodeType;
    private String templateCode;
    private String labelFormat;
    private BigDecimal labelWidthMm;
    private BigDecimal labelHeightMm;
    private String printParamsJson;
    private String actionType;
    private Integer reprintCount;
    private LocalDateTime lastReprintTime;
    private LocalDateTime createTime;
}
