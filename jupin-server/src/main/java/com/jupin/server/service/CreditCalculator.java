package com.jupin.server.service;

import org.springframework.stereotype.Component;

@Component
public class CreditCalculator {

    /**
     * 计算跳车扣分，按文档 1.7 梯度规则：
     * - 距开团 >24h 跳车：-10
     * - 距开团 2-24h 跳车：-20
     * - 距开团 <2h 跳车：-30
     * - 累犯（7 天内 > 2 次）：额外 -5
     */
    public int calculateLeaveDeduct(long hoursBeforeGame, int recentLeaveCount) {
        int base;
        if (hoursBeforeGame > 24) base = 10;
        else if (hoursBeforeGame >= 2) base = 20;
        else base = 30;

        if (recentLeaveCount > 2) base += 5;
        return Math.min(base, 50);
    }
}
