package com.wms.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.print.dto.KingdeeLabelPrintRequest;
import com.wms.print.entity.LabelPrintJob;
import com.wms.print.mapper.LabelPrintJobMapper;
import com.wms.print.service.LabelPrintJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 初始化与 PDA 收料 mock 一致的物料标签打印任务。
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "kingdee.cloud", name = "mock-enabled", havingValue = "true")
public class PdaMockLabelPrintInitializer implements CommandLineRunner {

    private final LabelPrintJobMapper jobMapper;
    private final LabelPrintJobService labelPrintJobService;

    private record MockLine(String sourceBillNo, String code, String name, String spec,
                            String batch, String unit, String qty, String prodDate) {}

    private static final MockLine[] LINES = {
            new MockLine("SLD20260706001", "MAT-10001", "电阻 10K", "10KΩ ±1%", "B20260701", "PCS", "1000", "2026-07-06"),
            new MockLine("SLD20260706001", "MAT-10002", "电容 100uF", "100uF/25V", "B20260702", "PCS", "500", "2026-07-06"),
            new MockLine("SLD20260706001", "MAT-10003", "PCB主板", "V2.1-A", "B20260703", "PCS", "200", "2026-07-06"),
            new MockLine("SLD20260705002", "MAT-20001", "连接器", "Type-C", "B20260628", "PCS", "300", "2026-07-05"),
            new MockLine("SLD20260705002", "MAT-20002", "屏蔽罩", "SUS304", "B20260629", "PCS", "300", "2026-07-05"),
            new MockLine("SLD20260704003", "MAT-30001", "螺丝 M3", "M3*8", "B20260620", "PCS", "5000", "2026-07-04"),
            new MockLine("SLD20260704003", "MAT-30002", "垫片", "φ3", "B20260621", "PCS", "5000", "2026-07-04"),
            new MockLine("SLD20260704003", "MAT-30003", "包装盒", "200*150", "B20260622", "PCS", "800", "2026-07-04"),
            new MockLine("SLD20260704003", "MAT-30004", "标签纸", "100*60", "B20260623", "PCS", "800", "2026-07-04"),
    };

    @Override
    public void run(String... args) {
        int created = 0;
        for (MockLine line : LINES) {
            if (exists(line.sourceBillNo(), line.code())) {
                continue;
            }
            KingdeeLabelPrintRequest req = new KingdeeLabelPrintRequest();
            req.setSourceBillNo(line.sourceBillNo());
            req.setMaterialCode(line.code());
            req.setMaterialName(line.name());
            req.setSpecification(line.spec());
            req.setBatchNo(line.batch());
            req.setProductionDate(line.prodDate());
            req.setQuantity(new BigDecimal(line.qty()));
            req.setUnitCode(line.unit());
            req.setBarcodeType("QR");
            req.setLabelWidthMm(new BigDecimal("60"));
            req.setLabelHeightMm(new BigDecimal("40"));
            req.setCopies(1);
            labelPrintJobService.createFromKingdee(req, "MANUAL");
            created++;
        }
        if (created > 0) {
            log.info("Initialized {} PDA mock label print jobs", created);
        }
    }

    private boolean exists(String sourceBillNo, String materialCode) {
        Long count = jobMapper.selectCount(new LambdaQueryWrapper<LabelPrintJob>()
                .eq(LabelPrintJob::getSourceBillNo, sourceBillNo)
                .eq(LabelPrintJob::getMaterialCode, materialCode));
        return count != null && count > 0;
    }
}
