package com.wms.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.mobile.dto.MobilePanelVerifyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class MobilePanelService {

    private static final Pattern PANEL_FORMAT = Pattern.compile("^(PLT|BM|P)[A-Z0-9\\-]{4,28}$", Pattern.CASE_INSENSITIVE);

    private final InventoryMapper inventoryMapper;
    private final BaseMaterialMapper materialMapper;

    public Map<String, Object> verify(MobilePanelVerifyRequest req) {
        if (!StringUtils.hasText(req.getPanelCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "板码不能为空");
        }
        String panelCode = req.getPanelCode().trim().toUpperCase();

        Map<String, Object> result = new HashMap<>();
        result.put("panelCode", panelCode);
        result.put("verifyTime", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        if (!PANEL_FORMAT.matcher(panelCode).matches()) {
            result.put("valid", false);
            result.put("result", "FAIL");
            result.put("message", "板码格式不正确，应以 PLT/BM/P 开头且长度合法");
            return result;
        }

        ParsedPanel parsed = parsePanelCode(panelCode);
        result.put("parsedMaterialCode", parsed.materialCode);
        result.put("parsedBatchNo", parsed.batchNo);
        result.put("parsedWarehouseCode", parsed.warehouseCode);

        String checkMaterial = StringUtils.hasText(req.getMaterialCode()) ? req.getMaterialCode() : parsed.materialCode;
        String checkBatch = StringUtils.hasText(req.getBatchNo()) ? req.getBatchNo() : parsed.batchNo;

        if (StringUtils.hasText(req.getMaterialCode()) && StringUtils.hasText(parsed.materialCode)
                && !req.getMaterialCode().equalsIgnoreCase(parsed.materialCode)) {
            result.put("valid", false);
            result.put("result", "FAIL");
            result.put("message", "板码解析物料与输入物料不一致");
            return result;
        }
        if (StringUtils.hasText(req.getBatchNo()) && StringUtils.hasText(parsed.batchNo)
                && !req.getBatchNo().equalsIgnoreCase(parsed.batchNo)) {
            result.put("valid", false);
            result.put("result", "FAIL");
            result.put("message", "板码解析批次与输入批次不一致");
            return result;
        }

        if (StringUtils.hasText(checkMaterial)) {
            BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                    .eq(BaseMaterial::getMaterialCode, checkMaterial));
            if (material == null) {
                result.put("valid", false);
                result.put("result", "FAIL");
                result.put("message", "物料编码不存在: " + checkMaterial);
                return result;
            }
            result.put("materialName", material.getMaterialName());
            result.put("unitCode", material.getUnitCode());
        }

        List<Inventory> stocks = queryPanelInventory(panelCode, checkMaterial, checkBatch, req.getWarehouseCode());
        List<Map<String, Object>> stockList = new ArrayList<>();
        BigDecimal totalQty = BigDecimal.ZERO;
        for (Inventory inv : stocks) {
            totalQty = totalQty.add(inv.getStockQty());
            Map<String, Object> row = new HashMap<>();
            row.put("warehouseCode", inv.getWarehouseCode());
            row.put("locationCode", inv.getLocationCode());
            row.put("materialCode", inv.getMaterialCode());
            row.put("batchNo", inv.getBatchNo());
            row.put("stockQty", inv.getStockQty());
            row.put("availableQty", inv.getAvailableQty());
            stockList.add(row);
        }
        result.put("stocks", stockList);
        result.put("totalStockQty", totalQty);

        if (StringUtils.hasText(checkMaterial) && stocks.isEmpty()) {
            result.put("valid", false);
            result.put("result", "FAIL");
            result.put("message", "板码校验失败：未找到对应库存记录");
            return result;
        }

        result.put("valid", true);
        result.put("result", "PASS");
        result.put("message", stocks.isEmpty()
                ? "板码格式校验通过（未绑定库存，仅格式有效）"
                : "板码校验通过，已匹配 " + stocks.size() + " 条库存");
        return result;
    }

    private List<Inventory> queryPanelInventory(String panelCode, String materialCode,
                                                  String batchNo, String warehouseCode) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(Inventory::getStockQty, 0);
        if (StringUtils.hasText(warehouseCode)) {
            wrapper.eq(Inventory::getWarehouseCode, warehouseCode);
        }
        if (StringUtils.hasText(materialCode)) {
            wrapper.eq(Inventory::getMaterialCode, materialCode);
        }
        if (StringUtils.hasText(batchNo)) {
            wrapper.eq(Inventory::getBatchNo, batchNo);
        } else {
            wrapper.and(w -> w.eq(Inventory::getBatchNo, panelCode)
                    .or().like(Inventory::getBatchNo, panelCode));
        }
        return inventoryMapper.selectList(wrapper);
    }

    private ParsedPanel parsePanelCode(String panelCode) {
        ParsedPanel p = new ParsedPanel();
        if (panelCode.startsWith("BM") && panelCode.length() >= 13) {
            p.materialCode = panelCode.substring(2, 13);
            if (panelCode.length() > 13) {
                p.batchNo = panelCode.substring(13);
            }
        } else if (panelCode.startsWith("PLT") && panelCode.length() >= 7) {
            p.warehouseCode = panelCode.substring(3, Math.min(7, panelCode.length()));
            if (panelCode.length() > 7) {
                p.batchNo = panelCode.substring(7);
            }
        } else if (panelCode.startsWith("P") && panelCode.length() >= 12) {
            p.materialCode = panelCode.substring(1, 12);
            if (panelCode.length() > 12) {
                p.batchNo = panelCode.substring(12);
            }
        }
        return p;
    }

    private static class ParsedPanel {
        String materialCode;
        String batchNo;
        String warehouseCode;
    }
}
