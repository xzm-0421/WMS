package com.wms.system.constant;

/**
 * 数据范围：1全部 2指定仓库 3本部门 4本部门及以下
 */
public final class DataScopeType {

    public static final int ALL = 1;
    public static final int CUSTOM_WAREHOUSE = 2;
    public static final int DEPT = 3;
    public static final int DEPT_AND_CHILD = 4;

    private DataScopeType() {
    }

    public static String label(int scope) {
        return switch (scope) {
            case ALL -> "全部数据";
            case CUSTOM_WAREHOUSE -> "指定仓库";
            case DEPT -> "本部门";
            case DEPT_AND_CHILD -> "本部门及以下";
            default -> "未知";
        };
    }
}
