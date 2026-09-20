package com.wms.pdabilllock.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pda_bill_lock")
public class PdaBillLock {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 单据类型，如 PURCHASE_RECEIVE / STOCK_COUNT */
    private String billType;
    private String billNo;
    private String lockUserId;
    private String lockUserName;
    private String lockDeviceNo;
    private LocalDateTime lockExpireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
