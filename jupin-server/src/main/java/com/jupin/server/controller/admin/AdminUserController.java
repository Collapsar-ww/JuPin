package com.jupin.server.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jupin.common.result.Result;
import com.jupin.pojo.entity.User;
import com.jupin.server.mapper.UserMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理后台-用户")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserMapper userMapper;

    @Operation(summary = "用户列表  🔒")
    @GetMapping("/list")
    public Result<List<User>> list(
            @RequestParam(required = false) Integer role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        Page<User> p = userMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<User>()
                        .eq(role != null, "role", role)
                        .eq(status != null, "status", status)
                        .orderByDesc("create_time"));
        return Result.success(p.getRecords());
    }

    @Operation(summary = "禁用/启用用户  🔒")
    @PutMapping("/{userId}/status")
    public Result<Void> setStatus(@PathVariable Long userId, @RequestBody Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) return Result.error("用户不存在");
        user.setStatus(status);
        userMapper.updateById(user);
        return Result.success();
    }
}
