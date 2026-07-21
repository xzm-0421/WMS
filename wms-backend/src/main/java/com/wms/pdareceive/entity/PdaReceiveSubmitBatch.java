package com.wms.pdareceive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("pda_receive_submit_batch")
public class PdaReceiveSubmitBatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String billType;
    private String direction;
    private String billNo;
    private String supplierCode;
    private String supplierName;
    private Integer lineCount;
    private BigDecimal totalQty;
    private String erpSyncStatus;
    private String erpBillNo;
    private String erpSyncMessage;
    private String operatorId;
    private String operatorName;
    private String deviceNo;
    private LocalDateTime submitTime;
}
