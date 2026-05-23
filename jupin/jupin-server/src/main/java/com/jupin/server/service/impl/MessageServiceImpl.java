package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.Message;
import com.jupin.server.mapper.MessageMapper;
import com.jupin.server.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;

    @Override
    public List<Message> getList(Long userId, Integer type, Integer page, Integer size) {
        Page<Message> p = messageMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Message>().eq("user_id", userId)
                        .eq(type != null, "type", type)
                        .orderByDesc("create_time"));
        return p.getRecords();
    }

    @Override
    public long getUnreadCount(Long userId) {
        return messageMapper.selectCount(new QueryWrapper<Message>().eq("user_id", userId).eq("is_read", 0));
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long msgId) {
        Message msg = messageMapper.selectById(msgId);
        if (msg == null || !msg.getUserId().equals(userId)) throw new BaseException("消息不存在");
        msg.setIsRead(1);
        msg.setReadTime(LocalDateTime.now());
        messageMapper.updateById(msg);
    }

    @Override
    @Transactional
    public void markAllRead(Long userId) {
        messageMapper.update(null, new UpdateWrapper<Message>()
                .set("is_read", 1).set("read_time", LocalDateTime.now())
                .eq("user_id", userId).eq("is_read", 0));
    }

    @Override
    @Transactional
    public void sendMessage(String msgKey, Long userId, int type, String title, String content, Long relatedId) {
        try {
            Message msg = Message.builder()
                    .msgKey(msgKey)
                    .userId(userId)
                    .type(type)
                    .title(title)
                    .content(content)
                    .relatedId(relatedId)
                    .isRead(0)
                    .build();
            messageMapper.insert(msg);
        } catch (Exception e) {
            // 重复消息忽略
        }
    }
}
