package com.wms.mes.dto;

import com.wms.mes.entity.MesOpPlan;
import com.wms.mes.entity.MesRouteOp;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MesOpPlanDetailVo {
    private MesOpPlan plan;
    private List<MesRouteOp> routeOps = new ArrayList<>();
}
