package com.wms.picking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PickIssueScanRequest {

    @NotBlank(message = "物料编码不能为空")
    private String materialCode;

    @NotBlank(message = "库位不能为空")
    private String locationCode;

    private String batchNo;

    @NotNull(message = "数量不能为空")
    private BigDecimal quantity;

    private Integer lineNo;

    private String barcodeContent;

    private String deviceNo;
}
