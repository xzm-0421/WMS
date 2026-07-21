package com.wms.barcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("barcode_rule")
public class BarcodeRule {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ruleCode;
    private String ruleName;
    private String appliesTo;
    private String barcodeType;
    private String templateCode;
    private String separator;
    private String segmentsJson;
    private String description;
    private String remark;
    private Integer versionNo;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
