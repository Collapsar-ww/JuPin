package com.jupin.server.service;

import com.jupin.pojo.vo.ChatMessageVO;

import java.util.List;

public interface ChatService {
    void sendMessage(Long userId, Long poolId, String content, String senderRole);
    List<ChatMessageVO> getHistory(Long userId, Long poolId, Integer page, Integer size);
}
