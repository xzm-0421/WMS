package com.wms.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 生产用料清单 PDA 联调种子（仓库/物料/库存）。
 * 单号 YLD20260715001，扫料条码示例：PITEST-001B20260715
 */
@Slf4j
@Component
@Order(80)
@RequiredArgsConstructor
public class ProductionIssueTestSeedInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        try {
            ensureWarehouse();
            ensureLocation();
            ensureMaterial("PITEST-001", "螺栓 M8", "M8×20");
            ensureMaterial("PITEST-002", "垫片 Φ8", "Φ8×1.5");
            ensureInventory("PITEST-001", 500);
            ensureInventory("PITEST-002", 300);
            log.info("Production issue test seed ready: bill=YLD20260715001 materials=PITEST-001/002 warehouse=CK004");
        } catch (Exception e) {
            log.warn("Production issue test seed skipped: {}", e.getMessage());
        }
    }

    private void ensureWarehouse() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM base_warehouse WHERE warehouse_code = 'CK004' AND deleted = 0",
                Integer.class);
        if (n != null && n > 0) {
            jdbcTemplate.update("""
                    UPDATE base_warehouse SET status = 1, deleted = 0,
                    erp_warehouse_code = CASE WHEN erp_warehouse_code IS NULL OR LTRIM(RTRIM(erp_warehouse_code)) = ''
                        THEN 'CK004' ELSE erp_warehouse_code END
                    WHERE warehouse_code = 'CK004'
                    """);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO base_warehouse (warehouse_code, warehouse_name, warehouse_type, status, create_by, erp_warehouse_code)
                VALUES ('CK004', N'原材料仓', 'RAW', 1, 'system', 'CK004')
                """);
    }

    private void ensureLocation() {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM base_location WHERE location_code = 'CK004-A01' AND deleted = 0",
                Integer.class);
        if (n != null && n > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO base_location (location_code, location_name, warehouse_code, zone_code, location_type, status, create_by)
                VALUES ('CK004-A01', N'A01货位', 'CK004', 'A', 'STORAGE', 1, 'system')
                """);
    }

    private void ensureMaterial(String code, String name, String spec) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM base_material WHERE material_code = ?",
                Integer.class, code);
        if (n != null && n > 0) {
            jdbcTemplate.update("""
                    UPDATE base_material SET deleted = 0, status = 1, material_name = ?, specification = ?
                    WHERE material_code = ?
                    """, name, spec, code);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO base_material
                (material_code, material_name, category_code, specification, unit_code, material_type, barcode_type, batch_managed, status, create_by)
                VALUES (?, ?, 'CAT001', ?, 'PCS', 'RAW', 'QR', 1, 1, 'system')
                """, code, name, spec);
    }

    private void ensureInventory(String materialCode, int qty) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM inventory
                WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
                  AND material_code = ? AND batch_no = 'B20260715'
                """, Integer.class, materialCode);
        if (n != null && n > 0) {
            jdbcTemplate.update("""
                    UPDATE inventory SET stock_qty = ?, available_qty = ?, frozen_qty = 0, update_time = GETDATE()
                    WHERE warehouse_code = 'CK004' AND location_code = 'CK004-A01'
                      AND material_code = ? AND batch_no = 'B20260715'
                    """, qty, qty, materialCode);
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO inventory
                (warehouse_code, location_code, material_code, batch_no, stock_qty, available_qty, frozen_qty, in_transit_qty, stock_status, inbound_date)
                VALUES ('CK004', 'CK004-A01', ?, 'B20260715', ?, ?, 0, 0, 'AVAILABLE', GETDATE())
                """, materialCode, qty, qty);
    }
}
