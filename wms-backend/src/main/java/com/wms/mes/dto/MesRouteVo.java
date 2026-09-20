package com.wms.mes.dto;

import com.wms.mes.entity.MesRoute;
import com.wms.mes.entity.MesRouteOp;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MesRouteVo {
    private MesRoute header;
    private List<MesRouteOp> operations = new ArrayList<>();
}
