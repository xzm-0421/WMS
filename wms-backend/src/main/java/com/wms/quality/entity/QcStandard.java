package com.wms.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qc_standard")
public class QcStandard {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String standardCode;
    private String standardName;
    private String materialCode;
    private String checkItems;
    private Integer status;
    private LocalDateTime createTime;
    private Integer deleted;
}
