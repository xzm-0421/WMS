package com.wms.barcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("barcode_serial_registry")
public class BarcodeSerialRegistry {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String serialNo;
    private String materialCode;
    private String batchNo;
    private Long barcodeInstanceId;
    private String status;
    private LocalDateTime createTime;
}
