package com.wms.incoming.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("purchase_return")
public class PurchaseReturn {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String returnNo;
    private String receiptRefNo;
    private String supplierCode;
    private String reason;
    private String status;
    private String creatorName;
    private LocalDateTime createTime;
    private Integer deleted;
}
