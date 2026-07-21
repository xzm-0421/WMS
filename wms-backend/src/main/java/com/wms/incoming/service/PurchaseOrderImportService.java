package com.wms.incoming.service;

import com.wms.common.excel.ExcelImportHelper;
import com.wms.common.excel.PurchaseOrderImportRow;
import com.wms.common.exception.BusinessException;
import com.wms.common.constant.ErrorCode;
import com.wms.incoming.dto.PurchaseOrderCreateRequest;
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
public class PurchaseOrderImportService {

    private final PurchaseOrderService purchaseOrderService;

    public List<String> importExcel(MultipartFile file, String operatorName) throws IOException {
        List<PurchaseOrderImportRow> allRows = ExcelImportHelper.readRows(file, PurchaseOrderImportRow.class);

        Map<String, PurchaseOrderCreateRequest> grouped = new LinkedHashMap<>();
        String lastSupplier = null;
        String lastWarehouse = null;
        int validCount = 0;

        for (int i = 0; i < allRows.size(); i++) {
            PurchaseOrderImportRow row = allRows.get(i);
            int excelRowNum = i + 2;
            if (isBlankRow(row)) {
                continue;
            }

            ExcelImportHelper.detectPurchaseOrderFormat(row, excelRowNum);

            if (StringUtils.hasText(row.getSupplierCode())) {
                lastSupplier = row.getSupplierCode().trim();
            } else if (lastSupplier != null) {
                row.setSupplierCode(lastSupplier);
            }
            if (StringUtils.hasText(row.getWarehouseCode())) {
                lastWarehouse = row.getWarehouseCode().trim();
            } else if (lastWarehouse != null) {
                row.setWarehouseCode(lastWarehouse);
            }

            validateRow(row, excelRowNum);

            String key = row.getSupplierCode().trim() + "|" + row.getWarehouseCode().trim();
            PurchaseOrderCreateRequest req = grouped.computeIfAbsent(key, k -> {
                PurchaseOrderCreateRequest r = new PurchaseOrderCreateRequest();
                r.setSupplierCode(row.getSupplierCode().trim());
                r.setWarehouseCode(row.getWarehouseCode().trim());
                r.setLines(new ArrayList<>());
                return r;
            });
            PurchaseOrderCreateRequest.LineItem line = new PurchaseOrderCreateRequest.LineItem();
            line.setMaterialCode(row.getMaterialCode().trim());
            line.setMaterialName(StringUtils.hasText(row.getMaterialName()) ? row.getMaterialName().trim() : null);
            line.setUnitCode(StringUtils.hasText(row.getUnitCode()) ? row.getUnitCode().trim() : "PCS");
            line.setOrderQty(row.getOrderQty());
            line.setBatchNo(StringUtils.hasText(row.getBatchNo()) ? row.getBatchNo().trim() : null);
            req.getLines().add(line);
            validCount++;
        }

        if (validCount == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Excel 中没有有效数据行，请先下载模板填写后再导入");
        }

        List<String> orderNos = new ArrayList<>();
        for (PurchaseOrderCreateRequest req : grouped.values()) {
            orderNos.add(purchaseOrderService.create(req, operatorName));
        }
        return orderNos;
    }

    /** 跳过完全空行；有物料编码但缺数量的行会在校验阶段给出明确提示 */
    private boolean isBlankRow(PurchaseOrderImportRow row) {
        return !StringUtils.hasText(row.getMaterialCode())
                && !StringUtils.hasText(row.getSupplierCode())
                && !StringUtils.hasText(row.getWarehouseCode())
                && row.getOrderQty() == null;
    }

    private void validateRow(PurchaseOrderImportRow row, int rowNum) {
        ExcelImportHelper.requireText(row.getSupplierCode(), rowNum, "供应商编码");
        ExcelImportHelper.requireText(row.getWarehouseCode(), rowNum, "仓库编码");
        ExcelImportHelper.requireText(row.getMaterialCode(), rowNum, "物料编码");
        ExcelImportHelper.requirePositiveQty(row.getOrderQty(), rowNum, "订单数量");
    }
}
