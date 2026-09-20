package com.wms.mes.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MesNameMaskTest {

    @Test
    void masksToFirstChar() {
        assertEquals("张*", MesNameMask.mask("张三"));
        assertEquals("*", MesNameMask.mask("李"));
        assertEquals("A*", MesNameMask.mask("Admin"));
    }
}
