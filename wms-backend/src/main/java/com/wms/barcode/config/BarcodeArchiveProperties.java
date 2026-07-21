package com.wms.barcode.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "wms.barcode.archive")
public class BarcodeArchiveProperties {

    /** 是否启用自动清理 */
    private boolean cleanupEnabled = true;

    /** 保留天数，超出则删除 */
    private int retentionDays = 365;

    /** 最大存档条数，超出则删除最旧记录 */
    private int maxRecords = 100000;
}
