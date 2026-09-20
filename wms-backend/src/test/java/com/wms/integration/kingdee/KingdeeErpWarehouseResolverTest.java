package com.wms.integration.kingdee;

import com.wms.base.entity.BaseWarehouse;
import com.wms.base.mapper.BaseWarehouseMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KingdeeErpWarehouseResolverTest {

    @Mock
    private BaseWarehouseMapper warehouseMapper;

    @InjectMocks
    private KingdeeErpWarehouseResolver resolver;

    @Test
    void preferReceiveLineWarehouse() {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        resolver = new KingdeeErpWarehouseResolver(warehouseMapper, props);
        assertEquals("CK008", resolver.resolve("WH01", "CK008"));
    }

    @Test
    void skipUnassignedLineWarehouse() {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        resolver = new KingdeeErpWarehouseResolver(warehouseMapper, props);
        assertNull(resolver.resolve(null, "CK004"));
        assertNull(resolver.firstAssigned("CK004", "", "CK004"));
        assertEquals("CK011", resolver.firstAssigned("CK004", "CK011"));
    }

    @Test
    void mapWmsWarehouseFromBaseData() {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        resolver = new KingdeeErpWarehouseResolver(warehouseMapper, props);
        BaseWarehouse warehouse = new BaseWarehouse();
        warehouse.setErpWarehouseCode("CK004");
        when(warehouseMapper.selectOne(any())).thenReturn(warehouse);
        assertEquals("CK004", resolver.resolve("WH01", null));
    }

    @Test
    void mapWmsWarehouseFromConfig() {
        KingdeeCloudProperties props = new KingdeeCloudProperties();
        props.setWarehouseMappings(Map.of("WH02", "CK005"));
        resolver = new KingdeeErpWarehouseResolver(warehouseMapper, props);
        when(warehouseMapper.selectOne(any())).thenReturn(null);
        assertEquals("CK005", resolver.resolve("WH02", null));
    }
}
