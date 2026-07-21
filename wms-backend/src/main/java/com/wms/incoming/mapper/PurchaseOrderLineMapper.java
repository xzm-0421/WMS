package com.wms.incoming.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.incoming.entity.PurchaseOrderLine;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseOrderLineMapper extends BaseMapper<PurchaseOrderLine> {
}
