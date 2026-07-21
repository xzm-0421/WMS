package com.wms.picking.service;

import com.wms.common.constant.ErrorCode;
import com.wms.common.excel.ExcelImportHelper;
import com.wms.common.excel.PrepNoticeImportRow;
import com.wms.common.exception.BusinessException;
import com.wms.picking.dto.PrepNoticeCreateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrepNoticeImportService {

    private final PrepNoticeService prepNoticeService;

    public List<String> importExcel(MultipartFile file, String operatorName) throws IOException {
        List<PrepNoticeImportRow> allRows = ExcelImportHelper.readRows(file, PrepNoticeImportRow.class);

        Map<String, PrepNoticeCreateRequest> grouped = new LinkedHashMap<>();
        String lastPlanNo = null;
        String lastWarehouse = null;
        int validCount = 0;

        for (int i = 0; i < allRows.size(); i++) {
            PrepNoticeImportRow row = allRows.get(i);
            int excelRowNum = i + 2;
            if (isBlankRow(row)) {
                continue;
            }

            if (StringUtils.hasText(row.getProductionPlanNo())) {
                lastPlanNo = row.getProductionPlanNo().trim();
            } else if (lastPlanNo != null) {
                row.setProductionPlanNo(lastPlanNo);
            }
            if (StringUtils.hasText(row.getWarehouseCode())) {
                lastWarehouse = row.getWarehouseCode().trim();
            } else if (lastWarehouse != null) {
                row.setWarehouseCode(lastWarehouse);
            }

            validateRow(row, excelRowNum);

            String planNo = StringUtils.hasText(row.getProductionPlanNo())
                    ? row.getProductionPlanNo().trim() : "DEFAULT";
            String key = planNo + "|" + row.getWarehouseCode().trim();
            PrepNoticeCreateRequest req = grouped.computeIfAbsent(key, k -> {
                PrepNoticeCreateRequest r = new PrepNoticeCreateRequest();
                r.setProductionPlanNo(planNo);
                r.setWarehouseCode(row.getWarehouseCode().trim());
                r.setLines(new ArrayList<>());
                return r;
            });
            PrepNoticeCreateRequest.LineItem line = new PrepNoticeCreateRequest.LineItem();
            line.setMaterialCode(row.getMaterialCode().trim());
            line.setMaterialName(StringUtils.hasText(row.getMaterialName()) ? row.getMaterialName().trim() : null);
            line.setDemandQty(row.getDemandQty());
            req.getLines().add(line);
            validCount++;
        }

        if (validCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 中没有有效数据行，请先下载模板填写后再导入");
        }

        List<String> noticeNos = new ArrayList<>();
        for (PrepNoticeCreateRequest req : grouped.values()) {
            noticeNos.add(prepNoticeService.create(req, operatorName));
        }
        return noticeNos;
    }

    private boolean isBlankRow(PrepNoticeImportRow row) {
        return !StringUtils.hasText(row.getMaterialCode())
                && !StringUtils.hasText(row.getWarehouseCode())
                && !StringUtils.hasText(row.getProductionPlanNo())
                && row.getDemandQty() == null;
    }

    private void validateRow(PrepNoticeImportRow row, int rowNum) {
        ExcelImportHelper.requireText(row.getWarehouseCode(), rowNum, "仓库编码");
        ExcelImportHelper.requireText(row.getMaterialCode(), rowNum, "物料编码");
        ExcelImportHelper.requirePositiveQty(row.getDemandQty(), rowNum, "需求数量");
    }
}
