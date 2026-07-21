package com.wms.incoming.dto;

import com.wms.incoming.entity.PurchaseOrder;
import com.wms.incoming.entity.PurchaseOrderLine;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PurchaseOrderVo {
    private PurchaseOrder order;
    private List<PurchaseOrderLine> lines;
    private BigDecimal progressPercent;
}
