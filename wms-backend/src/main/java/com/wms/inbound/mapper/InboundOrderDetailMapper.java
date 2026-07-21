package com.wms.inbound.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.inbound.entity.InboundOrderDetail;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InboundOrderDetailMapper extends BaseMapper<InboundOrderDetail> {
}
