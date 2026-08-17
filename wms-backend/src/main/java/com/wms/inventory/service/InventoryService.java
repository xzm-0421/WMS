package com.wms.inventory.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wms.base.entity.BaseMaterial;
import com.wms.base.mapper.BaseMaterialMapper;
import com.wms.common.constant.ErrorCode;
import com.wms.common.exception.BusinessException;
import com.wms.common.result.PageResult;
import com.wms.inventory.constant.StockStatus;
import com.wms.inventory.dto.InventoryChangeCommand;
import com.wms.inventory.dto.InventoryChangeResult;
import com.wms.inventory.dto.InventoryListVo;
import com.wms.inventory.dto.InventorySummaryDto;
import com.wms.inventory.dto.InventoryWarningDto;
import com.wms.inventory.entity.Inventory;
import com.wms.inventory.entity.InventoryTransaction;
import com.wms.inventory.entity.SafetyStock;
import com.wms.inventory.mapper.InventoryMapper;
import com.wms.inventory.mapper.InventoryQueryMapper;
import com.wms.inventory.mapper.InventoryTransactionMapper;
import com.wms.inventory.mapper.SafetyStockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final AtomicLong TXN_SEQ = new AtomicLong(1);

    private final InventoryMapper inventoryMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryQueryMapper inventoryQueryMapper;
    private final SafetyStockMapper safetyStockMapper;
    private final BaseMaterialMapper materialMapper;

    /**
     * 实时库存分页（附物料名称、规格、单位）。
     */
    public PageResult<InventoryListVo> pageList(String warehouseCode, String locationCode,
                                                String materialCode, String batchNo,
                                                long current, long size) {
        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(warehouseCode), Inventory::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(locationCode), Inventory::getLocationCode, locationCode)
                .eq(StringUtils.hasText(materialCode), Inventory::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(batchNo), Inventory::getBatchNo, batchNo)
                .gt(Inventory::getStockQty, 0)
                .orderByDesc(Inventory::getUpdateTime);
        Page<Inventory> page = inventoryMapper.selectPage(new Page<>(current, size), wrapper);
        List<InventoryListVo> records = toListVos(page.getRecords());
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private List<InventoryListVo> toListVos(List<Inventory> inventories) {
        if (inventories == null || inventories.isEmpty()) {
            return List.of();
        }
        Set<String> codes = new HashSet<>();
        for (Inventory inv : inventories) {
            if (StringUtils.hasText(inv.getMaterialCode())) {
                codes.add(inv.getMaterialCode().trim());
            }
        }
        Map<String, BaseMaterial> materialMap = Map.of();
        if (!codes.isEmpty()) {
            List<BaseMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<BaseMaterial>()
                    .in(BaseMaterial::getMaterialCode, codes));
            materialMap = materials.stream()
                    .filter(m -> StringUtils.hasText(m.getMaterialCode()))
                    .collect(Collectors.toMap(
                            m -> m.getMaterialCode().trim(),
                            Function.identity(),
                            (a, b) -> a));
        }
        List<InventoryListVo> result = new ArrayList<>(inventories.size());
        for (Inventory inv : inventories) {
            InventoryListVo vo = new InventoryListVo();
            vo.setId(inv.getId());
            vo.setMaterialCode(inv.getMaterialCode());
            vo.setLabelNo(inv.getBatchNo());
            vo.setBatchNo(inv.getBatchNo());
            vo.setAvailableQty(inv.getAvailableQty());
            vo.setWarehouseCode(inv.getWarehouseCode());
            vo.setProductionDate(inv.getProductionDate());
            vo.setCreateTime(inv.getCreateTime());
            vo.setStockQty(inv.getStockQty());
            vo.setLocationCode(inv.getLocationCode());
            vo.setFrozenQty(inv.getFrozenQty());
            vo.setStockStatus(inv.getStockStatus());
            BaseMaterial material = materialMap.get(
                    StringUtils.hasText(inv.getMaterialCode()) ? inv.getMaterialCode().trim() : "");
            if (material != null) {
                vo.setMaterialName(material.getMaterialName());
                vo.setSpecification(material.getSpecification());
                vo.setUnitCode(material.getUnitCode());
            }
            result.add(vo);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Inventory increase(InventoryChangeCommand cmd) {
        return increaseWithTransaction(cmd).getInventory();
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryChangeResult increaseWithTransaction(InventoryChangeCommand cmd) {
        if (cmd.getQuantity() == null || cmd.getQuantity().signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "入库数量必须大于0");
        }
        String batchNo = normalizeBatch(cmd.getBatchNo());
        Inventory inv = inventoryMapper.selectByKey(
                cmd.getWarehouseCode(), cmd.getLocationCode(), cmd.getMaterialCode(), batchNo);
        BigDecimal before = BigDecimal.ZERO;
        if (inv == null) {
            inv = new Inventory();
            inv.setWarehouseCode(cmd.getWarehouseCode());
            inv.setLocationCode(cmd.getLocationCode());
            inv.setMaterialCode(cmd.getMaterialCode());
            inv.setBatchNo(batchNo);
            inv.setStockQty(cmd.getQuantity());
            inv.setAvailableQty(cmd.getQuantity());
            inv.setFrozenQty(BigDecimal.ZERO);
            inv.setInTransitQty(BigDecimal.ZERO);
            inv.setInboundDate(LocalDateTime.now());
            inv.setStockStatus("AVAILABLE");
            inv.setCreateTime(LocalDateTime.now());
            inventoryMapper.insert(inv);
        } else {
            before = inv.getStockQty();
            inv.setStockQty(before.add(cmd.getQuantity()));
            inv.setAvailableQty(inv.getAvailableQty().add(cmd.getQuantity()));
            inv.setUpdateTime(LocalDateTime.now());
            inventoryMapper.updateById(inv);
        }
        String transactionNo = writeTransaction(cmd, batchNo, cmd.getQuantity(), before, inv.getStockQty());
        return new InventoryChangeResult(inv, transactionNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public Inventory decrease(InventoryChangeCommand cmd) {
        return decreaseWithTransaction(cmd).getInventory();
    }

    @Transactional(rollbackFor = Exception.class)
    public InventoryChangeResult decreaseWithTransaction(InventoryChangeCommand cmd) {
        if (cmd.getQuantity() == null || cmd.getQuantity().signum() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "出库数量必须大于0");
        }
        String batchNo = normalizeBatch(cmd.getBatchNo());
        Inventory inv = inventoryMapper.selectByKey(
                cmd.getWarehouseCode(), cmd.getLocationCode(), cmd.getMaterialCode(), batchNo);
        if (inv == null || inv.getAvailableQty().compareTo(cmd.getQuantity()) < 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "库存不足", "INSUFFICIENT_STOCK",
                    java.util.Map.of(
                            "materialCode", cmd.getMaterialCode(),
                            "warehouseCode", cmd.getWarehouseCode(),
                            "requestedQty", cmd.getQuantity(),
                            "availableQty", inv == null ? BigDecimal.ZERO : inv.getAvailableQty()));
        }
        BigDecimal before = inv.getStockQty();
        inv.setStockQty(before.subtract(cmd.getQuantity()));
        inv.setAvailableQty(inv.getAvailableQty().subtract(cmd.getQuantity()));
        inv.setUpdateTime(LocalDateTime.now());
        inventoryMapper.updateById(inv);
        String transactionNo = writeTransaction(cmd, batchNo, cmd.getQuantity().negate(), before, inv.getStockQty());
        return new InventoryChangeResult(inv, transactionNo);
    }

    private String writeTransaction(InventoryChangeCommand cmd, String batchNo,
                                  BigDecimal qty, BigDecimal before, BigDecimal after) {
        InventoryTransaction txn = new InventoryTransaction();
        txn.setTransactionNo(generateTxnNo());
        txn.setTransactionType(cmd.getTransactionType());
        txn.setWarehouseCode(cmd.getWarehouseCode());
        txn.setLocationCode(cmd.getLocationCode());
        txn.setMaterialCode(cmd.getMaterialCode());
        txn.setBatchNo(batchNo);
        txn.setTransactionQty(qty);
        txn.setBeforeQty(before);
        txn.setAfterQty(after);
        txn.setSourceOrderType(cmd.getSourceOrderType());
        txn.setSourceOrderNo(cmd.getSourceOrderNo());
        txn.setSourceOrderLine(cmd.getSourceOrderLine());
        txn.setOperatorId(cmd.getOperatorId());
        txn.setOperatorName(cmd.getOperatorName());
        txn.setDeviceNo(cmd.getDeviceNo());
        txn.setOperationTime(LocalDateTime.now());
        txn.setRemark(cmd.getRemark());
        txn.setCreateTime(LocalDateTime.now());
        transactionMapper.insert(txn);
        return txn.getTransactionNo();
    }

    private String normalizeBatch(String batchNo) {
        return StrUtil.blankToDefault(batchNo, "");
    }

    private String generateTxnNo() {
        return "TXN" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", TXN_SEQ.getAndIncrement() % 10000);
    }

    public PageResult<InventoryTransaction> pageTransactions(String warehouseCode, String materialCode,
                                                             String transactionType, long current, long size) {
        LambdaQueryWrapper<InventoryTransaction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(warehouseCode), InventoryTransaction::getWarehouseCode, warehouseCode)
                .eq(StringUtils.hasText(materialCode), InventoryTransaction::getMaterialCode, materialCode)
                .eq(StringUtils.hasText(transactionType), InventoryTransaction::getTransactionType, transactionType)
                .orderByDesc(InventoryTransaction::getOperationTime);
        Page<InventoryTransaction> page = transactionMapper.selectPage(new Page<>(current, size), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    public List<InventorySummaryDto> getSummary(String warehouseCode, String materialCode) {
        return inventoryQueryMapper.selectSummary(warehouseCode, materialCode);
    }

    public List<InventoryWarningDto> getWarnings(String warehouseCode) {
        List<InventoryWarningDto> warnings = new ArrayList<>();
        Map<String, BigDecimal> stockMap = new HashMap<>();

        List<InventorySummaryDto> summaries = inventoryQueryMapper.selectSummary(warehouseCode, null);
        for (InventorySummaryDto s : summaries) {
            stockMap.put(s.getWarehouseCode() + "|" + s.getMaterialCode(), s.getTotalAvailableQty());
        }

        List<SafetyStock> safetyStocks = safetyStockMapper.selectList(new LambdaQueryWrapper<SafetyStock>()
                .eq(SafetyStock::getStatus, 1)
                .eq(StringUtils.hasText(warehouseCode), SafetyStock::getWarehouseCode, warehouseCode));
        for (SafetyStock ss : safetyStocks) {
            String key = ss.getWarehouseCode() + "|" + ss.getMaterialCode();
            BigDecimal current = stockMap.getOrDefault(key, BigDecimal.ZERO);
            if (current.compareTo(ss.getSafetyQty()) < 0) {
                InventoryWarningDto dto = new InventoryWarningDto();
                dto.setWarehouseCode(ss.getWarehouseCode());
                dto.setMaterialCode(ss.getMaterialCode());
                dto.setCurrentQty(current);
                dto.setSafetyQty(ss.getSafetyQty());
                dto.setShortageQty(ss.getSafetyQty().subtract(current));
                fillMaterialName(dto);
                warnings.add(dto);
            }
        }

        List<BaseMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<BaseMaterial>()
                .gt(BaseMaterial::getSafetyStock, 0)
                .eq(BaseMaterial::getStatus, 1));
        for (BaseMaterial m : materials) {
            String wh = StringUtils.hasText(warehouseCode) ? warehouseCode : m.getDefaultWh();
            if (!StringUtils.hasText(wh)) {
                continue;
            }
            if (StringUtils.hasText(warehouseCode) && !warehouseCode.equals(wh)) {
                continue;
            }
            String key = wh + "|" + m.getMaterialCode();
            boolean alreadyWarned = warnings.stream()
                    .anyMatch(w -> w.getWarehouseCode().equals(wh) && w.getMaterialCode().equals(m.getMaterialCode()));
            if (alreadyWarned) {
                continue;
            }
            BigDecimal current = stockMap.getOrDefault(key, BigDecimal.ZERO);
            if (current.compareTo(m.getSafetyStock()) < 0) {
                InventoryWarningDto dto = new InventoryWarningDto();
                dto.setWarehouseCode(wh);
                dto.setMaterialCode(m.getMaterialCode());
                dto.setMaterialName(m.getMaterialName());
                dto.setCurrentQty(current);
                dto.setSafetyQty(m.getSafetyStock());
                dto.setShortageQty(m.getSafetyStock().subtract(current));
                warnings.add(dto);
            }
        }
        return warnings;
    }

    private void fillMaterialName(InventoryWarningDto dto) {
        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()
                .eq(BaseMaterial::getMaterialCode, dto.getMaterialCode()));
        if (material != null) {
            dto.setMaterialName(material.getMaterialName());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Inventory updateStockStatus(Long id, String stockStatus) {
        if (id == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "库存记录ID不能为空");
        }
        StockStatus status = StockStatus.fromCode(stockStatus)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "无效的库存状态: " + stockStatus));
        Inventory inv = inventoryMapper.selectById(id);
        if (inv == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "库存记录不存在");
        }
        inv.setStockStatus(status.getCode());
        inv.setUpdateTime(LocalDateTime.now());
        inventoryMapper.updateById(inv);
        return inv;
    }
}
