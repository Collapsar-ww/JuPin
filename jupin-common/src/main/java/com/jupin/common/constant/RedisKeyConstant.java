package com.jupin.common.constant;

public class RedisKeyConstant {
    public static final String JWT_BLACKLIST_PREFIX = "jwt:blacklist:";
    public static final String POOL_LOCATION_KEY = "pool:locations";
    public static final String POOL_LOCK_PREFIX = "pool:lock:";
    public static final String POOL_ROLE_PREFIX = "pool:roles:";
    public static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private RedisKeyConstant() {
    }
}
