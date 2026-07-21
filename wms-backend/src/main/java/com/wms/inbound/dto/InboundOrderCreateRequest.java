package com.wms.inbound.dto;

import com.wms.inbound.entity.InboundOrderDetail;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class InboundOrderCreateRequest {

    private String orderType;
    private String warehouseCode;
    private String supplierCode;
    private String productionOrderNo;
    private String sourceOrderNo;
    private LocalDate planDate;
    private String remark;
    private List<InboundOrderDetail> details;
}
