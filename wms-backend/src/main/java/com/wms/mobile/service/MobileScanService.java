package com.wms.mobile.service;



import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import com.wms.barcode.dto.BarcodeRecognizeResult;

import com.wms.barcode.service.BarcodeRecognizeService;

import com.wms.base.entity.BaseMaterial;

import com.wms.base.mapper.BaseMaterialMapper;

import com.wms.common.constant.ErrorCode;

import com.wms.common.exception.BusinessException;

import com.wms.inbound.dto.InboundScanRequest;

import com.wms.inbound.entity.InboundOrder;

import com.wms.inbound.entity.InboundOrderDetail;

import com.wms.inbound.mapper.InboundOrderDetailMapper;

import com.wms.inbound.mapper.InboundOrderMapper;

import com.wms.inbound.service.InboundService;

import com.wms.inventory.entity.Inventory;

import com.wms.inventory.mapper.InventoryMapper;

import com.wms.inventory.service.LocationAllocationService;

import com.wms.mobile.dto.MobileScanBarcodeRequest;

import com.wms.mobile.dto.MobileScanRecognizeRequest;

import com.wms.outbound.dto.OutboundScanRequest;

import com.wms.outbound.entity.OutboundOrder;

import com.wms.outbound.entity.OutboundOrderDetail;

import com.wms.outbound.mapper.OutboundOrderDetailMapper;

import com.wms.outbound.mapper.OutboundOrderMapper;

import com.wms.auth.security.LoginUser;

import com.wms.barcode.service.BarcodeTraceLinkService;

import com.wms.common.security.SecurityUtils;

import com.wms.inventory.dto.InventoryChangeCommand;

import com.wms.inventory.dto.InventoryChangeResult;

import com.wms.inventory.service.InventoryService;

import com.wms.outbound.service.OutboundService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.util.StringUtils;



import java.math.BigDecimal;

import java.util.ArrayList;

import java.util.HashMap;

import java.util.List;

import java.util.Map;



@Service

@RequiredArgsConstructor

public class MobileScanService {



    private final BaseMaterialMapper materialMapper;

    private final InventoryMapper inventoryMapper;

    private final InboundOrderMapper inboundOrderMapper;

    private final InboundOrderDetailMapper inboundDetailMapper;

    private final OutboundOrderMapper outboundOrderMapper;

    private final OutboundOrderDetailMapper outboundDetailMapper;

    private final InboundService inboundService;

    private final OutboundService outboundService;

    private final LocationAllocationService locationAllocationService;

    private final BarcodeRecognizeService barcodeRecognizeService;

    private final InventoryService inventoryService;

    private final BarcodeTraceLinkService barcodeTraceLinkService;



    /** 条码识别：规则引擎解析 + 物料主数据 */

    public Map<String, Object> recognize(MobileScanRecognizeRequest req) {

        if (!StringUtils.hasText(req.getBarcodeContent())) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "条码不能为空");

        }

        BarcodeRecognizeResult parsed = barcodeRecognizeService.recognize(req.getBarcodeContent());

        Map<String, Object> result = toRecognizeMap(parsed);



        if (StringUtils.hasText(parsed.getMaterialCode())) {

            BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()

                    .eq(BaseMaterial::getMaterialCode, parsed.getMaterialCode()));

            if (material != null) {

                result.put("materialName", material.getMaterialName());

                result.put("specification", material.getSpecification());

                result.put("unitCode", material.getUnitCode());

                result.put("materialExists", true);

            } else {

                result.put("materialExists", false);

                result.put("message", "物料编码未在系统中登记");

            }

        }

        return result;

    }



    /** 库存匹配：按条码/物料查可用库存 */

    public Map<String, Object> matchInventory(MobileScanRecognizeRequest req) {

        BarcodeRecognizeResult parsed = barcodeRecognizeService.recognize(req.getBarcodeContent());

        String materialCode = parsed.getMaterialCode();

        if (!StringUtils.hasText(materialCode)) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法从条码识别物料编码");

        }



        BaseMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<BaseMaterial>()

                .eq(BaseMaterial::getMaterialCode, materialCode));

        if (material == null) {

            throw new BusinessException(ErrorCode.NOT_FOUND, "物料不存在: " + materialCode, "MATERIAL_NOT_FOUND");

        }



        LambdaQueryWrapper<Inventory> wrapper = new LambdaQueryWrapper<>();

        wrapper.eq(Inventory::getMaterialCode, materialCode)

                .gt(Inventory::getAvailableQty, 0)

                .eq(StringUtils.hasText(req.getWarehouseCode()), Inventory::getWarehouseCode, req.getWarehouseCode())

                .eq(StringUtils.hasText(parsed.getBatchNo()), Inventory::getBatchNo, parsed.getBatchNo())

                .eq(StringUtils.hasText(parsed.getLocationCode()), Inventory::getLocationCode, parsed.getLocationCode())

                .orderByAsc(Inventory::getInboundDate);



        List<Inventory> stocks = inventoryMapper.selectList(wrapper);

        BigDecimal totalAvailable = stocks.stream()

                .map(Inventory::getAvailableQty)

                .reduce(BigDecimal.ZERO, BigDecimal::add);



        List<Map<String, Object>> stockList = new ArrayList<>();

        for (Inventory inv : stocks) {

            Map<String, Object> row = new HashMap<>();

            row.put("warehouseCode", inv.getWarehouseCode());

            row.put("locationCode", inv.getLocationCode());

            row.put("batchNo", inv.getBatchNo());

            row.put("stockQty", inv.getStockQty());

            row.put("availableQty", inv.getAvailableQty());

            stockList.add(row);

        }



        Map<String, Object> result = new HashMap<>();

        result.put("matched", !stocks.isEmpty());

        result.put("materialCode", materialCode);

        result.put("materialName", material.getMaterialName());

        result.put("specification", material.getSpecification());

        result.put("unitCode", material.getUnitCode());

        result.put("batchNo", parsed.getBatchNo());

        result.put("parseMode", parsed.getParseMode());

        result.put("totalAvailableQty", totalAvailable);

        result.put("stocks", stockList);

        if (stocks.isEmpty()) {

            result.put("message", "未找到可用库存");

        } else {

            Inventory first = stocks.get(0);

            result.put("recommendedLocation", first.getLocationCode());

            result.put("recommendedBatchNo", first.getBatchNo());

            result.put("recommendedAvailableQty", first.getAvailableQty());

        }

        return result;

    }



    /** 入库：扫条码自动匹配明细并登记 */

    @Transactional(rollbackFor = Exception.class)

    public Map<String, Object> inboundScanByBarcode(String orderNo, MobileScanBarcodeRequest req) {

        InboundOrder order = inboundOrderMapper.selectOne(new LambdaQueryWrapper<InboundOrder>()

                .eq(InboundOrder::getOrderNo, orderNo).eq(InboundOrder::getDeleted, 0));

        if (order == null) {

            throw new BusinessException(ErrorCode.NOT_FOUND, "入库单不存在");

        }



        BarcodeRecognizeResult parsed = barcodeRecognizeService.recognize(req.getBarcodeContent());

        if (!StringUtils.hasText(parsed.getMaterialCode())) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法识别物料编码");

        }



        InboundOrderDetail line = resolveInboundLine(orderNo, parsed.getMaterialCode(), req.getLineNo());

        BigDecimal qty = req.getQuantity() != null ? req.getQuantity() : BigDecimal.ONE;

        BigDecimal received = line.getReceivedQty() == null ? BigDecimal.ZERO : line.getReceivedQty();

        BigDecimal remaining = line.getOrderQty().subtract(received);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException(ErrorCode.CONFLICT, "该行已完成收货", "LINE_COMPLETED");

        }

        if (qty.compareTo(remaining) > 0) {

            qty = remaining;

        }



        String targetLocation = "";

        InboundScanRequest scan = new InboundScanRequest();

        scan.setLineNo(line.getLineNo());

        scan.setMaterialCode(parsed.getMaterialCode());

        scan.setBatchNo(StringUtils.hasText(parsed.getBatchNo()) ? parsed.getBatchNo() : line.getBatchNo());

        scan.setQuantity(qty);

        scan.setTargetLocation(targetLocation);

        scan.setBarcodeContent(parsed.getRaw());

        scan.setDeviceNo(req.getDeviceNo());



        Map<String, Object> result = inboundService.scanReceive(orderNo, scan);

        result.put("materialName", line.getMaterialName());

        result.put("materialCode", parsed.getMaterialCode());

        result.put("batchNo", StringUtils.hasText(parsed.getBatchNo()) ? parsed.getBatchNo() : line.getBatchNo());

        result.put("scannedQty", qty);

        result.put("targetLocation", targetLocation);

        result.put("parseMode", parsed.getParseMode());

        result.put("mode", "INBOUND");

        return result;

    }



    /** 出库：扫条码预览或确认扣减 */

    public Map<String, Object> outboundScanByBarcode(String orderNo, MobileScanBarcodeRequest req) {

        OutboundOrder order = outboundOrderMapper.selectOne(new LambdaQueryWrapper<OutboundOrder>()

                .eq(OutboundOrder::getOrderNo, orderNo).eq(OutboundOrder::getDeleted, 0));

        if (order == null) {

            throw new BusinessException(ErrorCode.NOT_FOUND, "出库单不存在");

        }



        BarcodeRecognizeResult parsed = barcodeRecognizeService.recognize(req.getBarcodeContent());

        if (!StringUtils.hasText(parsed.getMaterialCode())) {

            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法识别物料编码");

        }



        OutboundOrderDetail line = resolveOutboundLine(orderNo, parsed.getMaterialCode(), req.getLineNo());

        BigDecimal qty = req.getQuantity() != null ? req.getQuantity() : BigDecimal.ONE;

        BigDecimal issued = line.getIssuedQty() == null ? BigDecimal.ZERO : line.getIssuedQty();

        BigDecimal remaining = line.getDemandQty().subtract(issued);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {

            throw new BusinessException(ErrorCode.CONFLICT, "该行已完成出库", "LINE_COMPLETED");

        }

        if (qty.compareTo(remaining) > 0) {

            qty = remaining;

        }



        MobileScanRecognizeRequest matchReq = new MobileScanRecognizeRequest();

        matchReq.setBarcodeContent(req.getBarcodeContent());

        matchReq.setWarehouseCode(order.getWarehouseCode());

        Map<String, Object> inventoryMatch = matchInventory(matchReq);



        if (!Boolean.TRUE.equals(inventoryMatch.get("matched"))) {

            throw new BusinessException(ErrorCode.CONFLICT, "未找到可用库存", "NO_STOCK");

        }



        String sourceLocation = StringUtils.hasText(req.getSourceLocation())

                ? req.getSourceLocation()

                : (String) inventoryMatch.get("recommendedLocation");

        String batchNo = StringUtils.hasText(parsed.getBatchNo())

                ? parsed.getBatchNo()

                : (String) inventoryMatch.get("recommendedBatchNo");



        Map<String, Object> preview = new HashMap<>();

        preview.put("mode", "OUTBOUND");

        preview.put("preview", true);

        preview.put("orderNo", orderNo);

        preview.put("lineNo", line.getLineNo());

        preview.put("materialCode", parsed.getMaterialCode());

        preview.put("materialName", inventoryMatch.get("materialName"));

        preview.put("specification", inventoryMatch.get("specification"));

        preview.put("unitCode", inventoryMatch.get("unitCode"));

        preview.put("batchNo", batchNo);

        preview.put("sourceLocation", sourceLocation);

        preview.put("quantity", qty);

        preview.put("remainingDemandQty", remaining);

        preview.put("totalAvailableQty", inventoryMatch.get("totalAvailableQty"));

        preview.put("recommendedAvailableQty", inventoryMatch.get("recommendedAvailableQty"));

        preview.put("stocks", inventoryMatch.get("stocks"));

        preview.put("barcodeContent", parsed.getRaw());

        preview.put("parseMode", parsed.getParseMode());



        if (!Boolean.TRUE.equals(req.getConfirm())) {

            preview.put("message", "请确认后扣减库存");

            return preview;

        }



        OutboundScanRequest scan = new OutboundScanRequest();

        scan.setLineNo(line.getLineNo());

        scan.setMaterialCode(parsed.getMaterialCode());

        scan.setBatchNo(batchNo);

        scan.setQuantity(qty);

        scan.setSourceLocation(sourceLocation);

        scan.setBarcodeContent(parsed.getRaw());

        scan.setDeviceNo(req.getDeviceNo());



        Map<String, Object> result = outboundService.scanIssue(orderNo, scan);

        result.put("materialName", inventoryMatch.get("materialName"));

        result.put("specification", inventoryMatch.get("specification"));

        result.put("preview", false);

        result.put("parseMode", parsed.getParseMode());

        result.put("mode", "OUTBOUND");

        return result;

    }



    /** 无单扫码出库：预览或确认扣减 */

    @Transactional(rollbackFor = Exception.class)

    public Map<String, Object> directOutbound(MobileScanBarcodeRequest req) {

        MobileScanRecognizeRequest matchReq = new MobileScanRecognizeRequest();

        matchReq.setBarcodeContent(req.getBarcodeContent());

        matchReq.setWarehouseCode(req.getWarehouseCode());

        Map<String, Object> inventoryMatch = matchInventory(matchReq);



        if (!Boolean.TRUE.equals(req.getConfirm())) {

            BigDecimal qty = req.getQuantity() != null ? req.getQuantity() : BigDecimal.ONE;

            inventoryMatch.put("preview", true);

            inventoryMatch.put("quantity", qty);

            inventoryMatch.put("sourceLocation", inventoryMatch.get("recommendedLocation"));

            inventoryMatch.put("barcodeContent", req.getBarcodeContent());

            inventoryMatch.put("mode", "OUTBOUND");

            inventoryMatch.put("message", "请确认后扣减库存");

            return inventoryMatch;

        }



        if (!Boolean.TRUE.equals(inventoryMatch.get("matched"))) {

            throw new BusinessException(ErrorCode.CONFLICT, "未找到可用库存", "NO_STOCK");

        }



        BarcodeRecognizeResult parsed = barcodeRecognizeService.recognize(req.getBarcodeContent());

        BigDecimal qty = req.getQuantity() != null ? req.getQuantity() : BigDecimal.ONE;

        String sourceLocation = StringUtils.hasText(req.getSourceLocation())

                ? req.getSourceLocation()

                : (String) inventoryMatch.get("recommendedLocation");

        String batchNo = StringUtils.hasText(parsed.getBatchNo())

                ? parsed.getBatchNo()

                : (String) inventoryMatch.get("recommendedBatchNo");

        String warehouseCode = StringUtils.hasText(req.getWarehouseCode())

                ? req.getWarehouseCode()

                : (String) ((List<Map<String, Object>>) inventoryMatch.get("stocks")).get(0).get("warehouseCode");



        LoginUser user = SecurityUtils.currentUser();

        InventoryChangeResult changeResult = inventoryService.decreaseWithTransaction(InventoryChangeCommand.builder()

                .transactionType("PDA_OUTBOUND")

                .warehouseCode(warehouseCode)

                .locationCode(sourceLocation)

                .materialCode(parsed.getMaterialCode())

                .batchNo(batchNo)

                .quantity(qty)

                .sourceOrderType("PDA_OUTBOUND")

                .sourceOrderNo("SCAN")

                .operatorId(String.valueOf(user.getUserId()))

                .operatorName(user.getRealName() != null ? user.getRealName() : user.getUsername())

                .deviceNo(req.getDeviceNo())

                .remark(req.getBarcodeContent())

                .build());



        barcodeTraceLinkService.linkOutboundIssue(

                req.getBarcodeContent(),

                "PDA_SCAN",

                changeResult.getTransactionNo(),

                parsed.getMaterialCode(),

                batchNo,

                null);



        Map<String, Object> result = new HashMap<>();

        result.put("preview", false);

        result.put("mode", "OUTBOUND");

        result.put("materialCode", parsed.getMaterialCode());

        result.put("materialName", inventoryMatch.get("materialName"));

        result.put("specification", inventoryMatch.get("specification"));

        result.put("batchNo", batchNo);

        result.put("sourceLocation", sourceLocation);

        result.put("quantity", qty);

        result.put("transactionNo", changeResult.getTransactionNo());

        result.put("message", "出库成功");

        return result;

    }



    private Map<String, Object> toRecognizeMap(BarcodeRecognizeResult parsed) {

        Map<String, Object> result = new HashMap<>();

        result.put("barcodeContent", parsed.getRaw());

        result.put("materialCode", parsed.getMaterialCode());

        result.put("batchNo", parsed.getBatchNo());

        result.put("serialNo", parsed.getSerialNo());

        result.put("packBarcode", parsed.getPackBarcode());

        result.put("locationCode", parsed.getLocationCode());

        result.put("ruleCode", parsed.getRuleCode());

        result.put("versionNo", parsed.getVersionNo());

        result.put("instanceId", parsed.getInstanceId());

        result.put("parseMode", parsed.getParseMode());

        return result;

    }



    private InboundOrderDetail resolveInboundLine(String orderNo, String materialCode, Integer lineNo) {

        if (lineNo != null) {

            InboundOrderDetail line = inboundDetailMapper.selectOne(new LambdaQueryWrapper<InboundOrderDetail>()

                    .eq(InboundOrderDetail::getOrderNo, orderNo)

                    .eq(InboundOrderDetail::getLineNo, lineNo));

            if (line == null) {

                throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");

            }

            return line;

        }

        List<InboundOrderDetail> lines = inboundDetailMapper.selectList(new LambdaQueryWrapper<InboundOrderDetail>()

                .eq(InboundOrderDetail::getOrderNo, orderNo)

                .eq(InboundOrderDetail::getMaterialCode, materialCode)

                .orderByAsc(InboundOrderDetail::getLineNo));

        if (lines.isEmpty()) {

            throw new BusinessException(ErrorCode.CONFLICT, "单据中无此物料明细", "LINE_NOT_FOUND");

        }

        for (InboundOrderDetail line : lines) {

            BigDecimal received = line.getReceivedQty() == null ? BigDecimal.ZERO : line.getReceivedQty();

            if (received.compareTo(line.getOrderQty()) < 0) {

                return line;

            }

        }

        throw new BusinessException(ErrorCode.CONFLICT, "该物料所有明细行已完成", "ALL_LINES_COMPLETED");

    }



    private OutboundOrderDetail resolveOutboundLine(String orderNo, String materialCode, Integer lineNo) {

        if (lineNo != null) {

            OutboundOrderDetail line = outboundDetailMapper.selectOne(new LambdaQueryWrapper<OutboundOrderDetail>()

                    .eq(OutboundOrderDetail::getOrderNo, orderNo)

                    .eq(OutboundOrderDetail::getLineNo, lineNo));

            if (line == null) {

                throw new BusinessException(ErrorCode.NOT_FOUND, "明细行不存在");

            }

            return line;

        }

        List<OutboundOrderDetail> lines = outboundDetailMapper.selectList(new LambdaQueryWrapper<OutboundOrderDetail>()

                .eq(OutboundOrderDetail::getOrderNo, orderNo)

                .eq(OutboundOrderDetail::getMaterialCode, materialCode)

                .orderByAsc(OutboundOrderDetail::getLineNo));

        if (lines.isEmpty()) {

            throw new BusinessException(ErrorCode.CONFLICT, "单据中无此物料明细", "LINE_NOT_FOUND");

        }

        for (OutboundOrderDetail line : lines) {

            BigDecimal issued = line.getIssuedQty() == null ? BigDecimal.ZERO : line.getIssuedQty();

            if (issued.compareTo(line.getDemandQty()) < 0) {

                return line;

            }

        }

        throw new BusinessException(ErrorCode.CONFLICT, "该物料所有明细行已完成", "ALL_LINES_COMPLETED");

    }

}


