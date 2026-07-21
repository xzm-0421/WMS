package com.wms.barcode;

import com.wms.barcode.util.BatchNoNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BatchNoNormalizerTest {

    @Test
    void normalizesPipeWrappedBatch() {
        assertEquals("20260713", BatchNoNormalizer.normalize("|20260713|3"));
        assertEquals("20260710", BatchNoNormalizer.normalize("|20260710|1"));
    }

    @Test
    void parseFromPipeBarcode() {
        assertEquals("20260713",
                BatchNoNormalizer.parseFromPipeBarcode("S006-A315015D-0000-A01|20260713|1"));
        assertEquals("B20260703", BatchNoNormalizer.parseFromPipeBarcode("MAT-10003|B20260703"));
    }

    @Test
    void defaultReturnsNull() {
        assertNull(BatchNoNormalizer.normalize("DEFAULT"));
        assertEquals("DEFAULT", BatchNoNormalizer.forSubmit(null));
        assertEquals("20260713", BatchNoNormalizer.forSubmit("|20260713|1"));
    }
}
