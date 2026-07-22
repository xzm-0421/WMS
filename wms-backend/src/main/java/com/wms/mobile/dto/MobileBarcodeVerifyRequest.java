package com.wms.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobileBarcodeVerifyRequest {

    @NotBlank(message = "物料条码不能为空")
    private String barcodeContent;
    private String deviceNo;
}
