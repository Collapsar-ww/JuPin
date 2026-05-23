package com.jupin.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.server.mapper.PoolMemberMapper;
import com.jupin.server.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final PoolMemberMapper memberMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void sendMessage(Long userId, Long poolId, String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new BaseException("消息内容不能为空");
        }

        Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", userId).eq("status", MemberStatus.JOINED));
        if (count == 0) throw new BaseException("你不在该拼车群聊中");

        messagingTemplate.convertAndSend("/topic/pool/" + poolId + "/chat", Map.of(
                "userId", userId,
                "content", content,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
