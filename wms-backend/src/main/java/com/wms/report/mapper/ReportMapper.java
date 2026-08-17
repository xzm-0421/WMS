package com.wms.report.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReportMapper {

    @Select("SELECT COUNT(*) FROM inbound_order WHERE deleted = 0 AND CAST(create_time AS DATE) = CAST(GETDATE() AS DATE)")
    long countTodayInbound();

    @Select("SELECT COUNT(*) FROM outbound_order WHERE deleted = 0 AND CAST(create_time AS DATE) = CAST(GETDATE() AS DATE)")
    long countTodayOutbound();

    @Select("SELECT COUNT(DISTINCT material_code) FROM inventory WHERE stock_qty > 0")
    long countSku();

    @Select("""
            SELECT COUNT(*) FROM inbound_order WHERE deleted = 0 AND status IN ('PENDING', 'INBOUND')
            """)
    long countPendingInbound();

    @Select("""
            SELECT COUNT(*) FROM outbound_order WHERE deleted = 0 AND status IN ('PENDING', 'PICKING', 'OUTBOUND')
            """)
    long countPendingOutbound();

    @Select("SELECT COUNT(*) FROM stockcheck_task WHERE status = 'PENDING'")
    long countPendingStockcheck();

    @Select("SELECT COUNT(*) FROM qc_order WHERE status = 'PENDING'")
    long countPendingQc();

    @Select("""
            <script>
            SELECT warehouse_code AS warehouseCode, material_code AS materialCode,
                   SUM(stock_qty) AS totalStockQty, SUM(available_qty) AS totalAvailableQty
            FROM inventory WHERE stock_qty > 0
            <if test="warehouseCode != null and warehouseCode != ''">
              AND warehouse_code = #{warehouseCode}
            </if>
            GROUP BY warehouse_code, material_code
            ORDER BY warehouse_code, material_code
            </script>
            """)
    List<Map<String, Object>> inventorySummary(@Param("warehouseCode") String warehouseCode);

    @Select("""
            <script>
            SELECT order_type AS orderType, status, COUNT(*) AS cnt
            FROM inbound_order WHERE deleted = 0
            <if test="startDate != null and startDate != ''">
              AND plan_date &gt;= #{startDate}
            </if>
            <if test="endDate != null and endDate != ''">
              AND plan_date &lt;= #{endDate}
            </if>
            GROUP BY order_type, status
            </script>
            """)
    List<Map<String, Object>> inboundStatistics(@Param("startDate") String startDate,
                                                 @Param("endDate") String endDate);

    @Select("""
            <script>
            SELECT order_type AS orderType, status, COUNT(*) AS cnt
            FROM outbound_order WHERE deleted = 0
            <if test="startDate != null and startDate != ''">
              AND plan_date &gt;= #{startDate}
            </if>
            <if test="endDate != null and endDate != ''">
              AND plan_date &lt;= #{endDate}
            </if>
            GROUP BY order_type, status
            </script>
            """)
    List<Map<String, Object>> outboundStatistics(@Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);

    @Select("""
            SELECT CONVERT(VARCHAR(10), plan_date, 120) AS dayLabel, COUNT(*) AS cnt
            FROM inbound_order
            WHERE deleted = 0
              AND plan_date >= DATEADD(DAY, -6, CAST(GETDATE() AS DATE))
            GROUP BY plan_date
            ORDER BY plan_date
            """)
    List<Map<String, Object>> inboundDailyStatistics(@Param("startDate") String startDate,
                                                      @Param("endDate") String endDate);

    @Select("""
            SELECT CONVERT(VARCHAR(10), plan_date, 120) AS dayLabel, COUNT(*) AS cnt
            FROM outbound_order
            WHERE deleted = 0
              AND plan_date >= DATEADD(DAY, -6, CAST(GETDATE() AS DATE))
            GROUP BY plan_date
            ORDER BY plan_date
            """)
    List<Map<String, Object>> outboundDailyStatistics(@Param("startDate") String startDate,
                                                         @Param("endDate") String endDate);

    /** PDA 入库记录：近 7 天按创建日统计条数（不含已冲销） */
    @Select("""
            SELECT CONVERT(VARCHAR(10), create_time, 120) AS dayLabel, COUNT(*) AS cnt
            FROM pda_inbound_record
            WHERE deleted = 0
              AND ISNULL(status, '') <> 'REVERSED'
              AND create_time >= DATEADD(DAY, -6, CAST(GETDATE() AS DATE))
            GROUP BY CONVERT(VARCHAR(10), create_time, 120)
            ORDER BY dayLabel
            """)
    List<Map<String, Object>> pdaInboundDailyStatistics();

    /** PDA 出库记录：近 7 天按提交日统计批次条数 */
    @Select("""
            SELECT CONVERT(VARCHAR(10), submit_time, 120) AS dayLabel, COUNT(*) AS cnt
            FROM pda_receive_submit_batch
            WHERE direction = 'OUTBOUND'
              AND submit_time IS NOT NULL
              AND submit_time >= DATEADD(DAY, -6, CAST(GETDATE() AS DATE))
            GROUP BY CONVERT(VARCHAR(10), submit_time, 120)
            ORDER BY dayLabel
            """)
    List<Map<String, Object>> pdaOutboundDailyStatistics();

    @Select("""
            SELECT w.warehouse_code AS warehouseCode, w.warehouse_name AS warehouseName,
                   ISNULL(SUM(i.stock_qty), 0) AS totalQty
            FROM base_warehouse w
            LEFT JOIN inventory i ON i.warehouse_code = w.warehouse_code AND i.stock_qty > 0
            WHERE w.deleted = 0
            GROUP BY w.warehouse_code, w.warehouse_name
            """)
    List<Map<String, Object>> warehouseStockDistribution();

    @Select("SELECT ISNULL(SUM(stock_qty), 0) FROM inventory WHERE stock_qty > 0")
    java.math.BigDecimal totalStockQty();

    @Select("""
            SELECT w.warehouse_code AS warehouseCode, w.warehouse_name AS warehouseName,
                   w.rated_capacity AS ratedCapacity,
                   ISNULL(SUM(i.stock_qty), 0) AS currentStock
            FROM base_warehouse w
            LEFT JOIN inventory i ON i.warehouse_code = w.warehouse_code
            WHERE w.deleted = 0
            GROUP BY w.warehouse_code, w.warehouse_name, w.rated_capacity
            """)
    List<Map<String, Object>> warehouseUtilizationList();

    @Select("SELECT COUNT(*) FROM prep_notice WHERE deleted = 0 AND status = 'OPEN'")
    long countOpenPrepNotice();

    @Select("""
            SELECT CASE WHEN COUNT(*) = 0 THEN 100
            ELSE CAST(SUM(CASE WHEN diff_flag = 0 THEN 1 ELSE 0 END) * 100.0 / COUNT(*) AS DECIMAL(5,2))
            END FROM delivery_note WHERE deleted = 0
            AND create_time >= DATEADD(DAY, -30, GETDATE())
            """)
    double deliveryOnTimeRate();
}
