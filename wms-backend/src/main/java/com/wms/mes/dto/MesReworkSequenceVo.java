package com.wms.mes.dto;

import com.wms.mes.entity.MesDefect;
import com.wms.mes.entity.MesReworkOp;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MesReworkSequenceVo {
    private MesDefect defect;
    private List<MesReworkOp> operations = new ArrayList<>();
}
