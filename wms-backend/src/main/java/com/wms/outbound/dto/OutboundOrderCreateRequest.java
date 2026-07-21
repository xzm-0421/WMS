package com.wms.outbound.dto;

import com.wms.outbound.entity.OutboundOrderDetail;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class OutboundOrderCreateRequest {

    private String orderType;
    private String warehouseCode;
    private String productionOrderNo;
    private String customerCode;
    private LocalDate planDate;
    private String remark;
    private List<OutboundOrderDetail> details;
}
