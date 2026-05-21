package com.jupin.server.service;

import com.jupin.pojo.entity.Message;

import java.util.List;

public interface MessageService {
    List<Message> getList(Long userId, Integer type, Integer page, Integer size);
    long getUnreadCount(Long userId);
    void markRead(Long userId, Long msgId);
    void markAllRead(Long userId);
    void sendMessage(String msgKey, Long userId, int type, String title, String content, Long relatedId);
}
