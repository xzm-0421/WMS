package com.wms.mes.kingdee;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * OpenAPI 文档与云星空现场 FormId 可能大小写或标识不同，查询时按候选顺序探测。
 */
public final class MesKingdeeFormIds {

    private MesKingdeeFormIds() {
    }

    public static List<String> candidates(String configured) {
        Set<String> ids = new LinkedHashSet<>();
        if (StringUtils.hasText(configured)) {
            ids.add(configured.trim());
        }
        String key = configured == null ? "" : configured.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            case "ENG_ROUTE" -> {
                ids.add("ENG_ROUTE");
                ids.add("ENG_Route");
            }
            case "ENG_BOM", "PRD_BOM" -> {
                ids.add("ENG_BOM");
                ids.add("PRD_BOM");
            }
            case "PRD_PROCESSCHEDULE" -> {
                ids.add("PRD_PROCESSCHEDULE");
                ids.add("SFC_OperationPlanning");
            }
            case "SFC_OPERATIONPLANNING" -> {
                ids.add("SFC_OperationPlanning");
                ids.add("PRD_PROCESSCHEDULE");
            }
            case "ENG_EQUIPMENT" -> {
                ids.add("ENG_Equipment");
                ids.add("ENG_EQUIPMENT");
            }
            case "ENG_PROCESS" -> {
                ids.add("ENG_Process");
                ids.add("ENG_PROCESS");
            }
            default -> {
                // 保留配置值
            }
        }
        return new ArrayList<>(ids);
    }
}
