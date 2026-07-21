package com.wms.production.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.production.entity.ProductionOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {
}
