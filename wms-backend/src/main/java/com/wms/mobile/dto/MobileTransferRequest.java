package com.wms.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MobileTransferRequest {

    @NotBlank
    private String sourceLocation;
    @NotBlank
    private String materialCode;
    private String batchNo;
    @NotNull
    private BigDecimal transferQty;
    @NotBlank
    private String targetLocation;
    @NotBlank
    private String deviceNo;
}
