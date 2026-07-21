package com.wms.outbound.dto;

import com.wms.outbound.entity.OutboundOrder;
import com.wms.outbound.entity.OutboundOrderDetail;
import lombok.Data;

import java.util.List;

@Data
public class OutboundOrderVo {

    private OutboundOrder order;
    private List<OutboundOrderDetail> details;
}
