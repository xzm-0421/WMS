package com.wms.picking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("pick_issue")
public class PickIssue {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String issueNo;
    private String noticeNo;
    private String warehouseCode;
    private String handoverArea;
    private String status;
    private String pickerName;
    private LocalDateTime createTime;
    private Integer deleted;
}
