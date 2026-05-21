package com.jupin.server.service;

public interface ChatService {
    void sendMessage(Long userId, Long poolId, String content);
}
