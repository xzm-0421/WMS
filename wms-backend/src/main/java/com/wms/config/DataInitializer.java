package com.wms.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.auth.service.AuthService;
import com.wms.system.entity.SysUser;
import com.wms.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        initAdminUser();
        initDemoData();
    }

    private void initAdminUser() {
        String passwordHash = AuthService.sha256Hex("123456");
        String encoded = passwordEncoder.encode(passwordHash);
        SysUser existing = userMapper.selectByUsername("admin");
        if (existing != null) {
            if (!passwordEncoder.matches(passwordHash, existing.getPassword())) {
                SysUser update = new SysUser();
                update.setId(existing.getId());
                update.setPassword(encoded);
                userMapper.updateById(update);
                log.info("Admin password hash synchronized for default login");
            }
            return;
        }
        log.info("Initializing default admin user...");

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPassword(encoded);
        admin.setRealName("系统管理员");
        admin.setPhone("13800000000");
        admin.setEmail("admin@wms.local");
        admin.setStatus(1);
        admin.setWarehouseScopeJson("[\"WH01\",\"WH02\"]");
        admin.setCreateBy("system");
        userMapper.insert(admin);

        jdbcTemplate.update("INSERT INTO sys_role (role_code, role_name, description, data_scope, status) VALUES (?, ?, ?, ?, ?)",
                "SUPER_ADMIN", "超级管理员", "系统全部功能", 1, 1);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", admin.getId(), 1L);

        String[] perms = {"system:user:list", "system:user:add", "inbound:list", "outbound:list", "inventory:list"};
        for (String code : perms) {
            jdbcTemplate.update(
                    "INSERT INTO sys_permission (permission_code, permission_name, module, status) VALUES (?, ?, ?, 1)",
                    code, code, code.split(":")[0]);
            jdbcTemplate.update(
                    "INSERT INTO sys_role_permission (role_id, permission_id) SELECT 1, id FROM sys_permission WHERE permission_code = ?",
                    code);
        }
    }

    private void initDemoData() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM base_warehouse", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        log.info("Initializing demo master data...");
        jdbcTemplate.update("""
                INSERT INTO base_warehouse (warehouse_code, warehouse_name, warehouse_type, status)
                VALUES ('WH01', '原料仓', 'RAW', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO base_warehouse_zone (zone_code, zone_name, warehouse_code, status)
                VALUES ('A1', 'A区', 'WH01', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO base_location (location_code, location_name, warehouse_code, zone_code, location_type, status)
                VALUES ('WH01A1010101', '原料仓-A区-01排01列01层', 'WH01', 'A1', 'RAW', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO base_unit (unit_code, unit_name, status) VALUES ('KG', '千克', 1), ('PCS', '个', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO base_material_category (category_code, category_name, status)
                VALUES ('CAT001', '金属材料', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO base_material (material_code, material_name, category_code, unit_code, material_type, batch_managed, status)
                VALUES ('MAT00000123', '不锈钢板 304 2mm', 'CAT001', 'KG', 'RAW', 1, 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO base_supplier (supplier_code, supplier_name, status)
                VALUES ('SUP0001', '上海宝钢集团', 1)
                """);
        seedDemoInboundOrder();
    }

    private void seedDemoInboundOrder() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inbound_order WHERE order_no = 'RK202606250001'", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO inbound_order (order_no, order_type, warehouse_code, supplier_code, plan_date, status, creator_id, creator_name)
                VALUES ('RK202606250001', 'PURCHASE', 'WH01', 'SUP0001', CAST(GETDATE() AS DATE), 'PENDING', '1', '系统管理员')
                """);
        jdbcTemplate.update("""
                INSERT INTO inbound_order_detail (order_no, line_no, material_code, material_name, unit_code, order_qty, received_qty, target_location, line_status)
                VALUES ('RK202606250001', 1, 'MAT00000123', '不锈钢板 304 2mm', 'KG', 5000, 0, 'WH01A1010101', 'PENDING')
                """);
    }
}
