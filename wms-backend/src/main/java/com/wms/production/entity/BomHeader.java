package com.wms.production.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bom_header")
public class BomHeader {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String bomCode;
    private String productCode;
    private String versionNo;
    private Integer status;
    private LocalDateTime createTime;
}
