package com.wms.pdainbound.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PdaInboundSubmitRequest {

    @NotBlank(message = "条码不能为空")
    private String barcodeContent;

    @NotBlank(message = "仓库不能为空")
    private String warehouseCode;

    /** 可选库位；为空表示仅仓库入库 */
    private String locationCode;

    /** true=自动分配库位（可选） */
    private Boolean autoAllocateLocation;

    private String materialCode;
    private String batchNo;

    @NotNull(message = "入库数量不能为空")
    @DecimalMin(value = "0.0001", message = "入库数量必须大于0")
    private BigDecimal quantity;

    private String remark;
    private String deviceNo;
}
