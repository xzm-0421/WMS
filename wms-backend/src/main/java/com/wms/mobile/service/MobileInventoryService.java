package com.wms.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.auth.security.LoginUser;
import com.wms.barcode.dto.BarcodeRecognizeResult;
import com.wms.barcode.service.BarcodeRecognizeService;
import com.wms.base.entity.BaseLocation;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseLocationMapper;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryTransactionMapper;
import com.wms.inventoryext.service.TransferOrderService;
import com.wms.mobile.dto.MobileInventoryQueryRequest;
import com.wms.mobile.dto.MobileTraceRequest;
import com.wms.mobile.dto.MobileTransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MobileInventoryService {

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final TransferOrderService transferOrderService;
    private final BaseMaterialMapper materialMapper;
    private final BaseLocationMapper locationMapper;
    private final BarcodeRecognizeService barcodeRecognizeService;

    public Map<String, Object> query(MobileInventoryQueryRequest req) {
        String materialCode = req.getMaterialCode();
        String locationCode = req.getLocationCode();
        String batchNo = req.getBatchNo();
        if (StringUtils.hasText(req.getBarcode()) && !StringUtils.hasText(materialCode)) {
            BarcodeRecognizeResult recognized = barcodeRecognizeService.recognize(req.getBarcode());
            materialCode = recognized.getMaterialCode();
            if (!StringUtils.hasText(batchNo)) {
                batchNo = recognized.getBatchNo();
            }
            if (!StringUtils.hasText(locationCode)) {
                locationCode = recognized.getLocationCode();
            }
        }
        if ("LOCATION".equalsIgnoreCase(req.getQueryType()) && StringUtils.hasText(req.getBarcode())) {
            locationCode = req.getBarcode().trim();
        }
        if ("BATCH".equalsIgnoreCase(req.getQueryType()) && StringUtils.hasText(req.getBarcode())) {
            batchNo = req.getBarcode();
        }

        boolean locationQuery = "LOCATION".equalsIgnoreCase(req.getQueryType())
                || (StringUtils.hasText(locationCode) && !StringUtils.hasText(materialCode));
        if (locationQuery && StringUtils.hasText(locationCode)) {
            BaseLocation location = locationMapper.selectOne(new LambdaQueryWrapper<BaseLocation>()
                    .eq(BaseLocation::getLocationCode, locationCode.trim())
                    .eq(BaseLocation::getDeleted, 0)
                    .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY"));
            if (location == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "源库位不存在: " + locationCode.trim(),
                        "SOURCE_LOCATION_NOT_FOUND");
            }
        }

        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(req.getWarehouseCode()), Inventory::getWarehouseCode, req.getWarehouseCode())
                .eq(StringUtils.hasText(materialCode), Inventory::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(locationCode), Inventory::getLocationCode, locationCode)
                .eq(StringUtils.hasText(batchNo), Inventory::getBatchNo, batchNo)
                .gt(Inventory::getStockQty, 0);

        List<Inventory> stocks = inventoryMapper.selectList(wrapper);
        Map<String, Object> result = new HashMap<>();
        if (StringUtils.hasText(locationCode)) {
            result.put("locationCode", locationCode.trim());
        }
        if (stocks.isEmpty()) {
            result.put("stocks", List.of());
            result.put("totalStockQty", BigDecimal.ZERO);
            result.put("totalAvailableQty", BigDecimal.ZERO);
            if (locationQuery) {
                result.put("message", "该库位暂无可用库存");
            }
            return result;
        }

        Set<String> materialCodes = stocks.stream()
                .map(Inventory::getMaterialCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(HashSet::new));
        Map<String, BaseMaterial> materialMap = new HashMap<>();
        if (!materialCodes.isEmpty()) {
            List<BaseMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<BaseMaterial>()
                    .in(BaseMaterial::getMaterialCode, materialCodes));
            for (BaseMaterial material : materials) {
                materialMap.put(material.getMaterialCode(), material);
            }
        }

        String mainMaterial = materialCode != null ? materialCode : stocks.get(0).getMaterialCode();
        BaseMaterial material = materialMap.get(mainMaterial);
        if (material != null) {
            result.put("materialCode", material.getMaterialCode());
            result.put("materialName", material.getMaterialName());
            result.put("specification", material.getSpecification());
            result.put("unitCode", material.getUnitCode());
        }

        BigDecimal totalStock = BigDecimal.ZERO;
        BigDecimal totalAvailable = BigDecimal.ZERO;
        List<Map<String, Object>> stockList = new ArrayList<>();
        for (Inventory inv : stocks) {
            totalStock = totalStock.add(inv.getStockQty());
            totalAvailable = totalAvailable.add(inv.getAvailableQty());
            BaseMaterial rowMaterial = materialMap.get(inv.getMaterialCode());
            Map<String, Object> item = new HashMap<>();
            item.put("warehouseCode", inv.getWarehouseCode());
            item.put("locationCode", inv.getLocationCode());
            item.put("materialCode", inv.getMaterialCode());
            item.put("materialName", rowMaterial != null ? rowMaterial.getMaterialName() : inv.getMaterialCode());
            item.put("unitCode", rowMaterial != null ? rowMaterial.getUnitCode() : null);
            item.put("batchNo", inv.getBatchNo());
            item.put("stockQty", inv.getStockQty());
            item.put("availableQty", inv.getAvailableQty());
            item.put("frozenQty", inv.getFrozenQty());
            item.put("inboundDate", inv.getInboundDate());
            item.put("stockStatus", inv.getStockStatus());
            stockList.add(item);
        }
        result.put("stocks", stockList);
        result.put("totalStockQty", totalStock);
        result.put("totalAvailableQty", totalAvailable);
        result.put("totalFrozenQty", totalStock.subtract(totalAvailable));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transfer(MobileTransferRequest req) {
        LoginUser user = currentUser();
        String operator = user.getRealName() != null ? user.getRealName() : user.getUsername();
        return transferOrderService.createAndExecuteFromPda(req, operator);
    }

    public Map<String, Object> trace(MobileTraceRequest req) {
        String materialCode = req.getMaterialCode();
        String batchNo = req.getBatchNo();
        if (StringUtils.hasText(req.getBarcode()) && !StringUtils.hasText(materialCode)) {
            BarcodeRecognizeResult recognized = barcodeRecognizeService.recognize(req.getBarcode());
            materialCode = recognized.getMaterialCode();
            if (!StringUtils.hasText(batchNo)) {
                batchNo = recognized.getBatchNo();
            }
        }
        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(materialCode), InventoryTransaction::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(batchNo), InventoryTransaction::getBatchNo, batchNo)
                .orderByDesc(InventoryTransaction::getOperationTime)
                .last("OFFSET 0 ROWS FETCH NEXT 50 ROWS ONLY");
        List<InventoryTransaction> txns = transactionMapper.selectList(wrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("materialCode", materialCode);
        result.put("batchNo", batchNo);
        if (StringUtils.hasText(materialCode)) {
            BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                    .eq(BaseMaterial::getMaterialCode, materialCode));
            if (material != null) {
                result.put("materialName", material.getMaterialName());
            }
        }
        if (StringUtils.hasText(materialCode) && StringUtils.hasText(batchNo)) {
            Inventory inv = inventoryMapper.selectByKey(null, null, materialCode, batchNo);
            if (inv == null) {
                inv = findInventory(null, materialCode, batchNo);
            }
            if (inv != null) {
                result.put("currentStock", inv.getStockQty());
                result.put("currentLocation", inv.getLocationCode());
            }
        }

        List<Map<String, Object>> records = new ArrayList<>();
        int seq = 1;
        for (InventoryTransaction txn : txns) {
            Map<String, Object> row = new HashMap<>();
            row.put("seq", seq++);
            row.put("transactionType", txn.getTransactionType());
            row.put("warehouseCode", txn.getWarehouseCode());
            row.put("locationCode", txn.getLocationCode());
            row.put("qty", txn.getTransactionQty());
            row.put("sourceOrderNo", txn.getSourceOrderNo());
            row.put("operatorName", txn.getOperatorName());
            row.put("operationTime", txn.getOperationTime());
            records.add(row);
        }
        result.put("traceRecords", records);
        return result;
    }

    private Inventory findInventory(String location, String materialCode, String batchNo) {
        LambdaQueryWrapper<Inventory> w = new LambdaQueryWrapper<>();
        w.eq(StringUtils.hasText(location), Inventory::getLocationCode, location)
                .eq(StringUtils.hasText(materialCode), Inventory::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(batchNo), Inventory::getBatchNo, batchNo)
                .gt(Inventory::getStockQty, 0)
                .last("OFFSET 0 ROWS FETCH NEXT 1 ROWS ONLY");
        return inventoryMapper.selectOne(w);
    }

    private String guessWarehouse(String locationCode) {
        if (!StringUtils.hasText(locationCode)) {
            return "WH01";
        }
        return locationCode.length() >= 4 ? locationCode.substring(0, 4) : "WH01";
    }

    private LoginUser currentUser() {
        return (LoginUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
