package com.wms.mes;

/**
 * 轻 MES 状态与单据类型常量。
 */
public final class MesConstants {

    private MesConstants() {
    }

    public static final String SYNC_NOT_SYNCED = "NOT_SYNCED";
    public static final String SYNC_SYNCED = "SYNCED";
    public static final String SYNC_SYNCING = "SYNCING";
    public static final String SYNC_FAILED = "FAILED";
    public static final String SYNC_PENDING = "PENDING";
    public static final String SYNC_SUCCESS = "SUCCESS";
    public static final String SYNC_CANCELLED = "CANCELLED";
    public static final String SYNC_MANUAL = "MANUAL_REQUIRED";
    public static final String SYNC_STALE = "STALE";

    public static final String REPORT_NORMAL = "NORMAL";
    public static final String REPORT_REWORK = "REWORK";

    public static final String PLAN_RELEASED = "RELEASED";
    public static final String PLAN_RUNNING = "RUNNING";
    public static final String PLAN_PAUSED = "PAUSED";
    public static final String PLAN_CLOSED = "CLOSED";

    public static final String NETWORK_ONLINE = "ONLINE";
    public static final String NETWORK_OFFLINE = "OFFLINE";

    public static final String REWORK_REWORKING = "REWORKING";
    public static final String REWORK_DONE = "DONE";
    public static final String REWORK_SECONDARY = "SECONDARY";
    public static final String REWORK_CLOSED = "CLOSED";

    public static final String OP_PENDING = "PENDING";
    public static final String OP_DONE = "DONE";

    public static final String PREFIX_REPORT = "BG";
    public static final String PREFIX_TRANSFER = "ZY";
    public static final String PREFIX_DEFECT = "BL";

    public static final int FLAG_YES = 1;
    public static final int FLAG_NO = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_DISABLED = 0;
}
