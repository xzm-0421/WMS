package com.wms.mes.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MesTransferSubmitRequest {
    private String moNo;
    private String fromProcessCode;
    private String toProcessCode;
    private BigDecimal qty;
    private String remark;
}
