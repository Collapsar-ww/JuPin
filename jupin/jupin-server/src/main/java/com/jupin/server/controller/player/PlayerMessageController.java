package com.jupin.server.controller.player;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.entity.Message;
import com.jupin.pojo.vo.MessageVO;
import com.jupin.server.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "玩家端-消息")
@RestController
@RequestMapping("/api/player/message")
@RequiredArgsConstructor
public class PlayerMessageController {

    private final MessageService messageService;

    @Operation(summary = "消息列表  🔒")
    @GetMapping("/list")
    public Result<List<MessageVO>> list(@RequestParam(required = false) Integer type,
                                        @RequestParam(defaultValue = "1") Integer page,
                                        @RequestParam(defaultValue = "20") Integer size) {
        List<Message> msgs = messageService.getList(BaseContext.getCurrentId(), type, page, size);
        List<MessageVO> vos = msgs.stream().map(m -> BeanUtil.copyProperties(m, MessageVO.class)).collect(Collectors.toList());
        return Result.success(vos);
    }

    @Operation(summary = "未读消息数  🔒")
    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        return Result.success(Map.of("unreadCount", messageService.getUnreadCount(BaseContext.getCurrentId())));
    }

    @Operation(summary = "标记已读  🔒")
    @PutMapping("/read/{msgId}")
    public Result<Void> markRead(@PathVariable Long msgId) {
        messageService.markRead(BaseContext.getCurrentId(), msgId);
        return Result.success();
    }

    @Operation(summary = "全部已读  🔒")
    @PutMapping("/read-all")
    public Result<Void> markAllRead() {
        messageService.markAllRead(BaseContext.getCurrentId());
        return Result.success();
    }
}
