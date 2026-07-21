package com.wms.mobile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MobileScanBarcodeRequest {

    @NotBlank(message = "条码不能为空")
    private String barcodeContent;
    /** 每次扫码数量，默认 1 */
    private BigDecimal quantity;
    /** 入库目标库位（手动指定） */
    private String targetLocation;
    /** true=忽略 targetLocation，系统自动分配 */
    private Boolean autoAllocateLocation;
    /** 出库源库位（可选，缺省 FIFO 自动匹配） */
    private String sourceLocation;
    /** 指定明细行号（可选，缺省按物料自动匹配） */
    private Integer lineNo;
    /** 出库时：true=确认扣减，false=仅预览 */
    private Boolean confirm;
    /** 仓库编码（无单出库时用于库存匹配） */
    private String warehouseCode;
    private String deviceNo;
}
