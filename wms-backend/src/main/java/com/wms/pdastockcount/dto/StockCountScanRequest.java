package com.wms.pdastockcount.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockCountScanRequest {
    private String barcodeContent;
    private String materialCode;
    private String batchNo;
    private String locationCode;
    private Integer lineNo;
    /** 实盘数量；扫码匹配预览时可为空 */
    private BigDecimal actualQty;
    private String deviceNo;
}
