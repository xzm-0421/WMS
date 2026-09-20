package com.wms.mes.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MesRetryBackoffTest {

    @Test
    void exponentialMinutes() {
        LocalDateTime from = LocalDateTime.of(2026, 9, 2, 12, 0, 0);
        assertEquals(from.plusMinutes(1), MesRetryBackoff.nextRetryTime(0, from));
        assertEquals(from.plusMinutes(2), MesRetryBackoff.nextRetryTime(1, from));
        assertEquals(from.plusMinutes(16), MesRetryBackoff.nextRetryTime(4, from));
        assertTrue(MesRetryBackoff.exhausted(5, 5));
        assertFalse(MesRetryBackoff.exhausted(4, 5));
    }

    @Test
    void parameterErrorNotRetryable() {
        assertFalse(MesRetryBackoff.retryable("数据格式错误，请联系管理员处理。"));
        assertFalse(MesRetryBackoff.retryable("参数错误"));
        assertTrue(MesRetryBackoff.retryable("ERP系统响应慢"));
    }
}
