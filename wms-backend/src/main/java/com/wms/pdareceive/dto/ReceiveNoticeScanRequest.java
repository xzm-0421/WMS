package com.wms.pdareceive.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReceiveNoticeScanRequest {
    @NotBlank
    private String barcodeContent;
    private String deviceNo;
}
