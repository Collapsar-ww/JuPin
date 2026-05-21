package com.jupin.common.constant;

public class RoleConstant {
    public static final int PLAYER = 0;
    public static final int SHOP = 1;
    public static final int ADMIN = 2;

    public static String toPathPrefix(int role) {
        return switch (role) {
            case PLAYER -> "/api/player";
            case SHOP -> "/api/shop";
            case ADMIN -> "/api/admin";
            default -> "";
        };
    }
}
