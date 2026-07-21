package com.wms.mobile.dto;

import lombok.Data;

@Data
public class MobileLabelResolveRequest {
    private String barcodeContent;
    private String warehouseCode;
}
