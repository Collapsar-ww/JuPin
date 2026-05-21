package com.jupin.server.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DepositStrategy {

    public BigDecimal calculateDeposit(int creditScore) {
        if (creditScore >= 80) return BigDecimal.valueOf(10);
        if (creditScore >= 60) return BigDecimal.valueOf(20);
        return BigDecimal.valueOf(50);
    }
}
