package com.wms.inventoryext.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("other_outbound")
public class OtherOutbound {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String outboundType;
    private String warehouseCode;
    private String targetDesc;
    private String status;
    private String creatorName;
    private LocalDateTime createTime;
    private Integer deleted;
}
