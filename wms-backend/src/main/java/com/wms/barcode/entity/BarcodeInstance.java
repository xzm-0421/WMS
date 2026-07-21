package com.wms.barcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("barcode_instance")
public class BarcodeInstance {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String barcodeContent;
    private String ruleCode;
    private Integer versionNo;
    private String materialCode;
    private String batchNo;
    private String serialNo;
    private String packBarcode;
    private String barcodeType;
    private String sourceType;
    private String sourceRef;
    private Integer status;
    private LocalDateTime createTime;
}
