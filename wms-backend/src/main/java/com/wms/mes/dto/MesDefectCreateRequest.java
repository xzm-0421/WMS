package com.wms.mes.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MesDefectCreateRequest {
    private String moNo;
    private String sourceProcessCode;
    private BigDecimal defectQty;
    private String defectType;
    private String defectDesc;
    private String ownerName;
    private List<String> reworkProcessCodes;
}
