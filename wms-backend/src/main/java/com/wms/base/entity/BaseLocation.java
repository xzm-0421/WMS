package com.wms.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_location")
public class BaseLocation extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String locationCode;
    private String locationName;
    private String warehouseCode;
    private String zoneCode;
    private String locationType;
    private Integer rowNo;
    private Integer colNo;
    private Integer layerNo;
    private BigDecimal maxCapacity;
    private String capacityUnit;
    private Integer mixedMaterial;
    private Integer mixedBatch;
    /** IDLE / OCCUPIED / LOCKED */
    private String occupyStatus;
    private String qrCode;
    private Integer status;
    private String remark;
}
