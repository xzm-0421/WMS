package com.wms.config;

import com.wms.system.constant.PermissionCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class PermissionInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        for (PermissionCatalog perm : PermissionCatalog.all()) {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sys_permission WHERE permission_code = ? AND deleted = 0",
                    Integer.class, perm.getCode());
            if (count != null && count > 0) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO sys_permission (permission_code, permission_name, module, status) VALUES (?, ?, ?, 1)",
                    perm.getCode(), perm.getName(), perm.getModule());
            log.info("Initialized permission: {}", perm.getCode());
        }
        jdbcTemplate.update("""
                INSERT INTO sys_role_permission (role_id, permission_id)
                SELECT r.id, p.id FROM sys_role r
                CROSS JOIN sys_permission p
                WHERE r.role_code = 'SUPER_ADMIN' AND p.deleted = 0
                AND NOT EXISTS (
                    SELECT 1 FROM sys_role_permission rp
                    WHERE rp.role_id = r.id AND rp.permission_id = p.id
                )
                """);
    }
}
