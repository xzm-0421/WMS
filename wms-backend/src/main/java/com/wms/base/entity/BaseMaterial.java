package com.wms.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wms.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("base_material")
public class BaseMaterial extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String materialCode;
    private String materialName;
    private String categoryCode;
    private String specification;
    private String model;
    private String unitCode;
    private String materialType;
    private String barcodeType;
    private String barcodeRule;
    private Integer batchManaged;
    private Integer serialManaged;
    private Integer shelfLifeManaged;
    private LocalDate productionDate;
    private String defaultBatchNo;
    private BigDecimal safetyStock;
    private BigDecimal maxStock;
    private String abcClass;
    private String defaultWh;
    private String defaultLoc;
    private Integer status;
    private String remark;
}
