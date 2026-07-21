package com.wms.inventoryext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("other_inbound")
public class OtherInbound {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String inboundType;
    private String warehouseCode;
    private String sourceDesc;
    private String status;
    private String creatorName;
    private LocalDateTime createTime;
    private Integer deleted;
}
