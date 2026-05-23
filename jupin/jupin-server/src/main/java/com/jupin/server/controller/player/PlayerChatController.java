package com.jupin.server.controller.player;

import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.vo.ChatMessageVO;
import com.jupin.server.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "玩家端-群聊")
@RestController
@RequestMapping("/api/player/chat")
@RequiredArgsConstructor
public class PlayerChatController {

    private final ChatService chatService;

    @Operation(summary = "发送群聊消息  🔒")
    @PostMapping("/send")
    public Result<Void> send(@RequestBody Map<String, Object> body) {
        Long poolId = Long.valueOf(body.getOrDefault("poolId", body.get("pool_id")).toString());
        String content = body.get("content").toString();
        chatService.sendMessage(BaseContext.getCurrentId(), poolId, content, "player");
        return Result.success();
    }

    @Operation(summary = "群聊历史  🔒")
    @GetMapping("/history")
    public Result<List<ChatMessageVO>> history(
            @RequestParam Long poolId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {
        return Result.success(chatService.getHistory(BaseContext.getCurrentId(), poolId, page, size));
    }
}
