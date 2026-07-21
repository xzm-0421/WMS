package com.wms.outbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OutboundScanRequest {

    @NotNull
    private Integer lineNo;

    @NotBlank
    private String materialCode;

    private String batchNo;

    @NotNull
    private BigDecimal quantity;

    @NotBlank
    private String sourceLocation;

    private String barcodeContent;

    @NotBlank
    private String deviceNo;
}
