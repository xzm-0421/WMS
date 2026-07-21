package com.wms.production.dto;

import com.wms.production.entity.BomDetail;
import com.wms.production.entity.BomHeader;
import lombok.Data;

import java.util.List;

@Data
public class BomVo {

    private BomHeader header;
    private List<BomDetail> details;
}
