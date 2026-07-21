package com.wms.incoming.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.incoming.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
}
