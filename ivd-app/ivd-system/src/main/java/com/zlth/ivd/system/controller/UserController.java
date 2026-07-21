package com.zlth.ivd.system.controller;

import com.zlth.ivd.common.dto.PageVO;
import com.zlth.ivd.common.dto.Result;
import com.zlth.ivd.common.annotation.RequireRole;
import com.zlth.ivd.system.service.UserService;
import com.zlth.ivd.system.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户管理 — M00-1
 *
 * @author IVD Team
 * @since 2026-07-13
 */
@Tag(name = "用户管理", description = "系统用户 CRUD、角色分配、密码重置")
@RestController
@RequestMapping("/api/v1/system/users")
@RequiredArgsConstructor
@RequireRole("SYSTEM_ADMIN")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "分页查询用户列表")
    public Result<PageVO<UserVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status) {
        return Result.success(userService.list(page, pageSize, keyword, role, status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情")
    public Result<UserVO> getById(@PathVariable Long id) {
        return Result.success(userService.getById(id));
    }

    @PostMapping
    @Operation(summary = "新增用户")
    public Result<UserVO> create(@RequestBody Map<String, Object> body) {
        return Result.success(userService.create(
                (String) body.get("username"),
                (String) body.get("realName"),
                (String) body.get("role"),
                toLong(body.get("deptId")),
                (String) body.get("phone"),
                (String) body.get("email")));
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑用户")
    public Result<UserVO> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return Result.success(userService.update(id,
                (String) body.get("realName"),
                (String) body.get("role"),
                toLong(body.get("deptId")),
                (String) body.get("phone"),
                (String) body.get("email")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户（逻辑删除）")
    public Result<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/停用用户")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        userService.toggleStatus(id);
        return Result.success();
    }

    @PutMapping("/{id}/reset-password")
    @Operation(summary = "重置密码为默认密码")
    public Result<Void> resetPassword(@PathVariable Long id) {
        userService.resetPassword(id);
        return Result.success();
    }

    @GetMapping("/options")
    @Operation(summary = "用户下拉选项（按角色过滤）")
    public Result<List<UserVO>> options(@RequestParam(required = false) String role) {
        return Result.success(userService.options(role));
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
