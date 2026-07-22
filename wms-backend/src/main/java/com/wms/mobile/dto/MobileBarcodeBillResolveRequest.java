package com.wms.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobileBarcodeBillResolveRequest {

    @NotBlank(message = "单据条码不能为空")
    private String barcodeContent;
}
