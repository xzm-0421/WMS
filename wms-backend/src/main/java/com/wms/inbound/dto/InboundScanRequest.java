package com.wms.inbound.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class InboundScanRequest {

    @NotNull
    private Integer lineNo;

    @NotBlank
    private String materialCode;

    private String batchNo;

    @NotNull
    private BigDecimal quantity;

    @NotBlank
    private String targetLocation;

    private String productionDate;
    private String barcodeContent;

    @NotBlank
    private String deviceNo;
}
