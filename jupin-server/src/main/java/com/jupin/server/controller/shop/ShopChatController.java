package com.jupin.server.controller.shop;

import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.server.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "店家端-群聊")
@RestController
@RequestMapping("/api/shop/chat")
@RequiredArgsConstructor
public class ShopChatController {

    private final ChatService chatService;

    @Operation(summary = "发送群聊消息  🔒")
    @PostMapping("/send")
    public Result<Void> send(@RequestBody Map<String, Object> body) {
        Long poolId = Long.valueOf(body.get("pool_id").toString());
        String content = body.get("content").toString();
        chatService.sendMessage(BaseContext.getCurrentId(), poolId, content);
        return Result.success();
    }
}
