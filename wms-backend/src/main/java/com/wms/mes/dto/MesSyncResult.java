package com.wms.mes.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MesSyncResult {
    private String type;
    private int fetched;
    private int inserted;
    private int updated;
    private int skipped;
    private int rejected;
    private boolean success;
    private String message;
}
