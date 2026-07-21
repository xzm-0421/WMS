package com.wms.common.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public final class OrderNoGenerator {

    private static final AtomicInteger SEQ = new AtomicInteger(1);

    private OrderNoGenerator() {
    }

    public static String next(String prefix) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return prefix + date + String.format("%04d", SEQ.getAndIncrement() % 10000);
    }
}
