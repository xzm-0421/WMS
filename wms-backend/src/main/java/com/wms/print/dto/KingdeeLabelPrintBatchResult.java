package com.wms.print.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 金蝶批量标签打印响应。
 */
@Data
public class KingdeeLabelPrintBatchResult {

    private int count;
    private List<LabelPrintJobVo> jobs = new ArrayList<>();
    /** 逐行绝对打印地址（带 autoPrint） */
    private List<String> printUrls = new ArrayList<>();
    /** 首行打印地址，便于单窗口先打一张 */
    private String firstPrintUrl;
}
