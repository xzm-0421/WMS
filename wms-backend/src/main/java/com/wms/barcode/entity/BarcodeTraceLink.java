package com.wms.barcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("barcode_trace_link")
public class BarcodeTraceLink {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String barcodeContent;
    private Long barcodeInstanceId;
    private String refType;
    private String refNo;
    private String transactionNo;
    private String materialCode;
    private String batchNo;
    private String serialNo;
    private String remark;
    private LocalDateTime createTime;
}
