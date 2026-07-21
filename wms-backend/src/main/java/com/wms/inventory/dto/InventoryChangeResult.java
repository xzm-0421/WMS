package com.wms.inventory.dto;

import com.wms.inventory.entity.Inventory;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InventoryChangeResult {
    private final Inventory inventory;
    private final String transactionNo;
}
