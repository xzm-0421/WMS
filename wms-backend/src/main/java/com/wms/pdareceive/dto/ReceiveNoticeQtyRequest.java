package com.wms.pdareceive.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReceiveNoticeQtyRequest {

    /** 本次待提交领取数量（非累计扫描量） */
    @NotNull
    private BigDecimal qty;
}
