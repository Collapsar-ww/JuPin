package com.jupin.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.constant.MemberStatus;
import com.jupin.common.constant.PoolStatus;
import com.jupin.common.constant.ErrorConstant;
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
            throw new BaseException(ErrorConstant.MESSAGE_CONTENT_EMPTY);
        }

        CarPool pool = poolMapper.selectById(poolId);
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);
        if (pool.getStatus() == PoolStatus.FINISHED || pool.getStatus() == PoolStatus.CANCELLED) {
            throw new BaseException(ErrorConstant.POOL_FINISHED_CANNOT_SEND);
        }

        Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", userId).eq("status", MemberStatus.JOINED));
        if (count == 0) throw new BaseException(ErrorConstant.NOT_IN_POOL_CHAT);

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
        if (pool == null) throw new BaseException(ErrorConstant.POOL_NOT_FOUND);

        Long count = memberMapper.selectCount(new QueryWrapper<PoolMember>()
                .eq("pool_id", poolId).eq("user_id", userId).eq("status", MemberStatus.JOINED));
        if (count == 0) throw new BaseException(ErrorConstant.NOT_POOL_MEMBER);

        Page<ChatMessage> pageResult = chatMessageMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ChatMessage>()
                        .eq("pool_id", poolId)
                        .orderByDesc("create_time"));
        return pageResult.getRecords().stream()
                .map(msg -> BeanUtil.copyProperties(msg, ChatMessageVO.class))
                .collect(Collectors.toList());
    }
}
