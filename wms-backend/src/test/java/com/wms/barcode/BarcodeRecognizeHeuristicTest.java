package com.wms.barcode;

import com.wms.barcode.dto.BarcodeRecognizeResult;
import com.wms.barcode.mapper.BarcodeInstanceMapper;
import com.wms.barcode.mapper.BarcodeRuleMapper;
import com.wms.barcode.service.BarcodeRecognizeService;
import com.wms.barcode.service.BarcodeService;
import com.wms.base.mapper.BaseMaterialMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarcodeRecognizeHeuristicTest {

    @Mock
    private BarcodeInstanceMapper instanceMapper;
    @Mock
    private BarcodeRuleMapper ruleMapper;
    @Mock
    private BarcodeService barcodeService;
    @Mock
    private BaseMaterialMapper materialMapper;
    @Mock
    private com.wms.print.mapper.LabelPrintJobMapper labelPrintJobMapper;

    private BarcodeRecognizeService service;

    @BeforeEach
    void setUp() {
        service = new BarcodeRecognizeService(instanceMapper, ruleMapper, barcodeService, materialMapper, labelPrintJobMapper);
        when(instanceMapper.selectOne(any())).thenReturn(null);
        when(ruleMapper.selectList(any())).thenReturn(java.util.List.of());
        when(materialMapper.selectOne(any())).thenReturn(null);
    }

    @Test
    void recognizesPrintedLabelBarcode() {
        BarcodeRecognizeResult result = service.recognize("MAT-10001B20260701");
        assertEquals("MAT-10001", result.getMaterialCode());
        assertEquals("B20260701", result.getBatchNo());
    }

    @Test
    void recognizesPlainMaterialCode() {
        BarcodeRecognizeResult result = service.recognize("MAT-10002");
        assertEquals("MAT-10002", result.getMaterialCode());
    }

    @Test
    void recognizesPipeDelimitedBarcode() {
        BarcodeRecognizeResult result = service.recognize("MAT-10003|B20260703");
        assertEquals("MAT-10003", result.getMaterialCode());
        assertEquals("B20260703", result.getBatchNo());
    }

    @Test
    void recognizesPipeDelimitedBarcodeWithQuantity() {
        BarcodeRecognizeResult result = service.recognize("MAT-10001|B20260701|1000");
        assertEquals("MAT-10001", result.getMaterialCode());
        assertEquals("B20260701", result.getBatchNo());
        assertEquals(0, new BigDecimal("1000").compareTo(result.getQuantity()));
        assertEquals("PIPE", result.getQuantitySource());
        assertEquals(0, new BigDecimal("1000").compareTo(service.resolveScanQuantity("MAT-10001|B20260701|1000", result)));
    }

    @Test
    void recognizesJsonBarcodeWithFullFields() {
        String json = "{\"materialCode\":\"MAT-10002\",\"materialName\":\"电容 100uF\","
                + "\"specification\":\"100uF/25V\",\"batchNo\":\"B20260702\",\"qty\":500,\"unitCode\":\"PCS\"}";
        BarcodeRecognizeResult result = service.recognize(json);
        assertEquals("MAT-10002", result.getMaterialCode());
        assertEquals("电容 100uF", result.getMaterialName());
        assertEquals("100uF/25V", result.getSpecification());
        assertEquals("B20260702", result.getBatchNo());
        assertEquals("PCS", result.getUnitCode());
        assertEquals(0, new BigDecimal("500").compareTo(result.getQuantity()));
    }

    @Test
    void recognizesExtendedPipeBarcode() {
        BarcodeRecognizeResult result = service.recognize("MAT-10001|电阻 10K|10KΩ ±1%|B20260701|1000|PCS");
        assertEquals("MAT-10001", result.getMaterialCode());
        assertEquals("电阻 10K", result.getMaterialName());
        assertEquals("10KΩ ±1%", result.getSpecification());
        assertEquals("B20260701", result.getBatchNo());
        assertEquals("PCS", result.getUnitCode());
        assertEquals(0, new BigDecimal("1000").compareTo(result.getQuantity()));
    }

    @Test
    void recognizesJsonBarcodeWithQuantity() {
        BarcodeRecognizeResult result = service.recognize("{\"materialCode\":\"MAT-10002\",\"batchNo\":\"B20260702\",\"qty\":500}");
        assertEquals("MAT-10002", result.getMaterialCode());
        assertEquals("B20260702", result.getBatchNo());
        assertNotNull(result.getQuantity());
        assertEquals(0, new BigDecimal("500").compareTo(result.getQuantity()));
    }
}
