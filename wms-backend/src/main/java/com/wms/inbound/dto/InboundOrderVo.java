package com.wms.inbound.dto;

import com.wms.inbound.entity.InboundOrder;
import com.wms.inbound.entity.InboundOrderDetail;
import lombok.Data;

import java.util.List;

@Data
public class InboundOrderVo {

    private InboundOrder order;
    private List<InboundOrderDetail> details;
}
