package com.wms.mobile.dto;

import lombok.Data;

@Data
public class MobileScanRecognizeRequest {

    private String barcodeContent;
    private String warehouseCode;
}
