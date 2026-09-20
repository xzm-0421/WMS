package com.wms.print.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 金蝶批量发起物料标签打印。
 */
@Data
public class KingdeeLabelPrintBatchRequest {

    /** 是否自动打开打印（响应中 printUrl 带 autoPrint=1） */
    private Boolean autoPrint = true;

    @NotEmpty
    @Valid
    private List<KingdeeLabelPrintRequest> lines;
}
