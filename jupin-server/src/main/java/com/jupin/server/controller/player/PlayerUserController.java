package com.jupin.server.controller.player;

import cn.hutool.core.bean.BeanUtil;
import com.jupin.common.context.BaseContext;
import com.jupin.common.result.Result;
import com.jupin.pojo.dto.UserUpdateRequest;
import com.jupin.pojo.entity.User;
import com.jupin.pojo.vo.UserVO;
import com.jupin.server.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "玩家端-用户")
@RestController
@RequestMapping("/api/player/user")
@RequiredArgsConstructor
public class PlayerUserController {

    private final UserService userService;

    @Operation(summary = "个人信息  🔒")
    @GetMapping("/me")
    public Result<UserVO> me() {
        User user = userService.getCurrentUser(BaseContext.getCurrentId());
        return Result.success(BeanUtil.copyProperties(user, UserVO.class));
    }

    @Operation(summary = "修改个人信息  🔒")
    @PutMapping("/me")
    public Result<UserVO> updateMe(@Valid @RequestBody UserUpdateRequest request) {
        User user = userService.updateUser(BaseContext.getCurrentId(), request);
        return Result.success(BeanUtil.copyProperties(user, UserVO.class));
    }
}
