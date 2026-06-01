package com.jupin.server.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeoutMessage {
    public static final String ORDER_PAYMENT = "ORDER_PAYMENT";
    public static final String POOL_START = "POOL_START";
    public static final String COMPLETED_CONFIRM = "COMPLETED_CONFIRM";
    public static final String FINISHED_CONFIRM = "FINISHED_CONFIRM";

    private String type;
    private Long orderId;
    private Long poolId;
    private Long userId;
}
