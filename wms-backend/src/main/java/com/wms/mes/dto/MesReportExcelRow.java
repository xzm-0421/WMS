package com.wms.mes.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class MesReportExcelRow {

    @ExcelProperty("报工单号")
    private String reportNo;
    @ExcelProperty("工单号")
    private String moNo;
    @ExcelProperty("工序编码")
    private String processCode;
    @ExcelProperty("工序名称")
    private String processName;
    @ExcelProperty("报工类型")
    private String reportType;
    @ExcelProperty("数量")
    private BigDecimal qty;
    @ExcelProperty("重量kg")
    private BigDecimal weightKg;
    @ExcelProperty("设备")
    private String equipmentName;
    @ExcelProperty("操作员")
    private String operatorName;
    @ExcelProperty("报工时间")
    private LocalDateTime reportTime;
    @ExcelProperty("同步状态")
    private String syncStatus;
    @ExcelProperty("同步时间")
    private LocalDateTime syncTime;
    @ExcelProperty("ERP单号")
    private String erpBillNo;
    @ExcelProperty("失败原因")
    private String failReason;
}
