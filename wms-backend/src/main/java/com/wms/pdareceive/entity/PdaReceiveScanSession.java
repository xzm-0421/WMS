package com.wms.pdareceive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("pda_receive_scan_session")
public class PdaReceiveScanSession {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 单据类型，如 PURCHASE_RECEIVE / SALES_DELIVERY */
    private String billType;
    /** INBOUND / OUTBOUND */
    private String direction;
    private String billNo;
    private String supplierCode;
    private String supplierName;
    private LocalDate billDate;
    private String warehouseCode;
    /** NEW / SCANNING / COMPLETED（部分或全部提交、可处理余量全 0 均为已完成，列表不展示） */
    private String status;
    private Integer totalLines;
    private Integer checkedLines;
    private Integer submittedLines;
    private String deviceNo;
    private String operatorId;
    private String operatorName;
    private LocalDateTime lastScanTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
