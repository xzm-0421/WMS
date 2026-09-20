package com.wms.mes.dto;

import com.wms.mes.entity.MesEquipment;
import com.wms.mes.entity.MesOpPlan;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MesReportContextVo {
    private String moNo;
    private boolean normalCompleted;
    private List<String> allowedReportTypes = new ArrayList<>();
    private String typeHint;
    private List<MesOpPlan> plans = new ArrayList<>();
    private List<MesEquipment> equipment = new ArrayList<>();
    private List<String> reworkProcessCodes = new ArrayList<>();
    private List<String> openDefectNos = new ArrayList<>();
}
