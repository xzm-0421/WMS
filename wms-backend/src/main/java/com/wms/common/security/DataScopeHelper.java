package com.wms.common.security;

import com.wms.auth.security.LoginUser;
import com.wms.system.constant.DataScopeType;

import java.util.List;

public final class DataScopeHelper {

    private DataScopeHelper() {
    }

    public static boolean isUnrestricted(LoginUser user) {
        return user.isSuperAdmin()
                || user.getDataScope() == null
                || user.getDataScope() == DataScopeType.ALL;
    }

    public static List<String> allowedWarehouses(LoginUser user) {
        if (isUnrestricted(user)) {
            return null;
        }
        return user.getWarehouseScope();
    }

    public static boolean canAccessWarehouse(LoginUser user, String warehouseCode) {
        if (warehouseCode == null || warehouseCode.isBlank()) {
            return true;
        }
        if (isUnrestricted(user)) {
            return true;
        }
        List<String> scope = user.getWarehouseScope();
        return scope != null && scope.contains(warehouseCode);
    }
}
