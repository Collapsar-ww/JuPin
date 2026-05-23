package com.jupin.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.exception.BaseException;
import com.jupin.pojo.entity.CarPool;
import com.jupin.pojo.entity.ChatMessage;
import com.jupin.pojo.entity.PoolMember;
import com.jupin.pojo.entity.User;
import com.jupin.pojo.vo.ChatMessageVO;
import com.jupin.server.mapper.ChatMessageMapper;
import com.jupin.server.mapper.PoolMapper;
import com.jupin.server.mapper.PoolMemberMapper;
import com.jupin.server.mapper.UserMapper;
import com.jupin.server.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatMessageMapper chatMessageMapper;
    private final PoolMemberMapper memberMapper;
    private final PoolMapper poolMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void sendMessage(Long userId, Long poolId, String content, String senderRole) {
        if (content == null || content.trim().isEmpty()) {
            throw new BaseException("消息内容不能为空");
        }

        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");
        if (pool.getStatus() == PoolStatus.FINISHED || pool.getStatus() == PoolStatus.CANCELLED) {
            throw new BaseException("拼车已结束，无法发送消息");
        }

        Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", userId).eq("status", MemberStatus.JOINED));
        if (count == 0) throw new BaseException("你不在该拼车群聊中");

        User user = userMapper.selectById(userId);
        ChatMessage msg = ChatMessage.builder()
                .poolId(poolId)
                .senderId(userId)
                .senderName(user != null ? user.getNickname() : "")
                .senderRole(senderRole)
                .content(content.trim())
                .build();
        chatMessageMapper.insert(msg);
    }

    @Override
    public List<ChatMessageVO> getHistory(Long userId, Long poolId, Integer page, Integer size) {
        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException("拼车不存在");

        Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", userId).eq("status", MemberStatus.JOINED));
        if (count == 0) throw new BaseException("你不是该拼车成员");

        Page<ChatMessage> p = chatMessageMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ChatMessage>()
                        .eq("pool_id", poolId)
                        .orderByDesc("create_time"));
        return p.getRecords().stream()
                .map(m -> BeanUtil.copyProperties(m, ChatMessageVO.class))
                .collect(Collectors.toList());
    }
}
