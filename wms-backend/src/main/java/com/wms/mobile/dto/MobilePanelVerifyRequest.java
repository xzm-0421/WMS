package com.wms.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MobilePanelVerifyRequest {

    @NotBlank(message = "板码不能为空")
    private String panelCode;
    /** 可选：用于交叉校验的物料编码 */
    private String materialCode;
    /** 可选：用于交叉校验的批次号 */
    private String batchNo;
    /** 可选：仓库编码 */
    private String warehouseCode;
    private String deviceNo;
}
