package com.wms.incoming.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("delivery_note")
public class DeliveryNote {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deliveryNo;
    private String purchaseOrderNo;
    private String supplierCode;
    private LocalDateTime deliveryDate;
    private String status;
    private Integer diffFlag;
    private String remark;
    private LocalDateTime createTime;
    private Integer deleted;
}
