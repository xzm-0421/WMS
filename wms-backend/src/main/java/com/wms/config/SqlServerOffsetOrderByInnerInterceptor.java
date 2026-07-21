package com.wms.config;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.Locale;

/**
 * SQL Server 要求 OFFSET/FETCH 前必须有 ORDER BY。
 * MyBatis-Plus selectOne 及部分手写 .last("OFFSET ...") 会生成无 ORDER BY 的语句，此处统一补全。
 */
public class SqlServerOffsetOrderByInnerInterceptor implements InnerInterceptor {

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter,
                            RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
            throws SQLException {
        String sql = boundSql.getSql();
        if (sql == null) {
            return;
        }
        String upper = sql.toUpperCase(Locale.ROOT);
        if (upper.contains(" OFFSET ") && !upper.contains("ORDER BY")) {
            int offsetIdx = upper.lastIndexOf(" OFFSET ");
            if (offsetIdx > 0) {
                String fixed = sql.substring(0, offsetIdx) + " ORDER BY (SELECT NULL)" + sql.substring(offsetIdx);
                PluginUtils.mpBoundSql(boundSql).sql(fixed);
            }
        }
    }
}
