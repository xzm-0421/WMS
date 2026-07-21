package com.wms.picking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("material_pickup")
public class MaterialPickup {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String pickupNo;
    private String issueNo;
    private String receiverName;
    private LocalDateTime pickupTime;
    private String status;
    private LocalDateTime createTime;
}
