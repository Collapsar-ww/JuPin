package com.jupin.common.constant;

public class PoolStatus {
    public static final int OPEN = 0;           // 开放招募
    public static final int FULL = 1;           // 满员
    public static final int COMPLETED = 2;      // 拼车成功（已释放押金）
    public static final int FINISHED = 3;       // 剧本杀完成（已释放车费）
    public static final int CANCELLED = 4;      // 已取消
}
