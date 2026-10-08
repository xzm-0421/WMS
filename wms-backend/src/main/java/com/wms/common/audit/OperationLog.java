package com.wms.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要写入系统操作日志的接口方法，由 {@link com.wms.common.audit.OperationLogAspect} 处理。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {

    /** 业务模块，如 轻MES / 系统管理 / 认证 */
    String module();

    /** 操作类型，如 CREATE / UPDATE / DELETE / SUBMIT / RETRY / LOGIN */
    String type();

    /** 操作内容描述 */
    String content() default "";
}
