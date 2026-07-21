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
@TableName("base_warehouse")
public class BaseWarehouse extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    /** 金蝶仓库编码 FStockId */
    private String erpWarehouseCode;
    private String factoryCode;
    private String address;
    private String geoLocation;
    private BigDecimal areaSqm;
    private BigDecimal ratedCapacity;
    private Integer status;
    private String remark;
    private String phone;
}
