package com.jupin.server.service;

import com.jupin.pojo.entity.Order;
import com.jupin.pojo.dto.MockPayCallbackRequest;

import java.util.List;

public interface OrderService {
    Order create(Long userId, Long poolId, Integer type, String idempotentKey);
    void pay(Long userId, String orderNo);
    Order mockPayCallback(Long userId, MockPayCallbackRequest request);
    void refund(String orderNo);
    void release(Long orderId);
    Order getByNo(String orderNo);
    List<Order> myOrders(Long userId, Integer type, Integer status, Integer page, Integer size);
    List<Order> shopOrders(Long shopId, Integer status, Integer page, Integer size);
}
