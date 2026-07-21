package com.wms.mobile.controller;



import com.wms.barcode.dto.BarcodeRecognizeResult;

import com.wms.barcode.service.BarcodeRecognizeService;

import com.wms.common.result.ApiResult;

import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.Data;

import lombok.RequiredArgsConstructor;

import org.springframework.util.StringUtils;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;



import java.util.HashMap;

import java.util.Map;



@Tag(name = "PDA-条码")

@RestController

@RequestMapping("/mobile/barcode")

@RequiredArgsConstructor

public class MobileBarcodeController {



    private final BarcodeRecognizeService barcodeRecognizeService;



    @Operation(summary = "PDA条码解析（规则引擎）")

    @PostMapping("/parse")

    public ApiResult<Map<String, Object>> parse(@RequestBody MobileBarcodeParseRequest request) {

        if (!StringUtils.hasText(request.getBarcodeContent())) {

            return ApiResult.ok(Map.of("barcodeContent", "", "segments", Map.of()));

        }

        BarcodeRecognizeResult result = barcodeRecognizeService.recognize(request.getBarcodeContent());

        Map<String, Object> body = new HashMap<>();

        body.put("barcodeContent", result.getRaw());

        body.put("parseMode", result.getParseMode());

        body.put("ruleCode", result.getRuleCode());

        Map<String, String> segments = new HashMap<>();

        if (StringUtils.hasText(result.getMaterialCode())) {

            segments.put("materialCode", result.getMaterialCode());

        }

        if (StringUtils.hasText(result.getMaterialName())) {

            segments.put("materialName", result.getMaterialName());

        }

        if (StringUtils.hasText(result.getSpecification())) {

            segments.put("specification", result.getSpecification());

        }

        if (StringUtils.hasText(result.getUnitCode())) {

            segments.put("unitCode", result.getUnitCode());

        }

        if (StringUtils.hasText(result.getBatchNo())) {

            segments.put("batchNo", result.getBatchNo());

        }

        if (StringUtils.hasText(result.getSerialNo())) {

            segments.put("serialNo", result.getSerialNo());

        }

        if (StringUtils.hasText(result.getPackBarcode())) {

            segments.put("packBarcode", result.getPackBarcode());

        }

        if (StringUtils.hasText(result.getLocationCode())) {

            segments.put("locationCode", result.getLocationCode());

        }

        if (result.getQuantity() != null) {

            segments.put("quantity", result.getQuantity().stripTrailingZeros().toPlainString());

        }

        body.put("quantity", result.getQuantity());

        body.put("quantitySource", result.getQuantitySource());

        body.put("segments", segments);

        return ApiResult.ok(body);

    }



    @Data

    public static class MobileBarcodeParseRequest {

        private String barcodeContent;

    }

}

