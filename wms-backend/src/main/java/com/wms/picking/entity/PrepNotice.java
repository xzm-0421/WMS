package com.wms.picking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("prep_notice")
public class PrepNotice {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String noticeNo;
    private String productionPlanNo;
    private String warehouseCode;
    private LocalDateTime demandTime;
    private String status;
    private String creatorName;
    private LocalDateTime createTime;
    private Integer deleted;
}
