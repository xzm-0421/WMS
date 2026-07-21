package com.wms.stockcheck.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.stockcheck.entity.StockcheckTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StockcheckTaskMapper extends BaseMapper<StockcheckTask> {
}
