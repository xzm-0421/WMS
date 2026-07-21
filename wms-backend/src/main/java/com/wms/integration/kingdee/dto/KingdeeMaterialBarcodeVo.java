package com.wms.integration.kingdee.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KingdeeMaterialBarcodeVo {
    private String materialCode;
    private String materialName;
    private String barCode;
    private String packBarCode;
    private Boolean batchManaged;
    private Boolean serialManaged;
}
