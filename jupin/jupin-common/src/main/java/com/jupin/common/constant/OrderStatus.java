package com.jupin.common.constant;

public class OrderStatus {
    public static final int PENDING = 0;       // 待支付
    public static final int PAID = 1;          // 已支付
    public static final int REFUNDED = 2;      // 已退款
    public static final int FORFEITED = 3;     // 已扣留(跳车)
    public static final int OVERDUE = 4;       // 逾期
}
