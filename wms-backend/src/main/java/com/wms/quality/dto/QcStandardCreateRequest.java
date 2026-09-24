package com.wms.quality.dto;

import lombok.Data;

@Data
public class QcStandardCreateRequest {

    private String standardName;
    private String materialCode;
    private String materialName;
    private String qcType;
    private String qcItems;
}
