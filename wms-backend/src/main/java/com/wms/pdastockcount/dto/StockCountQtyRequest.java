package com.wms.pdastockcount.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockCountQtyRequest {
    @NotNull(message = "实盘数量不能为空")
    private BigDecimal actualQty;
}
