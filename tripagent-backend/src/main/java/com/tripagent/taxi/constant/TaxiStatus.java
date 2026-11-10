package com.tripagent.taxi.constant;

public final class TaxiStatus {

    public static final String CREATED = "CREATED";
    public static final String COMPLETED = "COMPLETED";
    public static final String CANCELLED = "CANCELLED";

    private TaxiStatus() {
    }

    public static boolean isValid(String status) {
        return CREATED.equals(status) || COMPLETED.equals(status) || CANCELLED.equals(status);
    }

    public static String text(String status) {
        return switch (status) {
            case CREATED -> "已创建";
            case COMPLETED -> "已完成";
            case CANCELLED -> "已取消";
            default -> status;
        };
    }
}
