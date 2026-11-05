package com.company.feedback.controller;

import com.company.feedback.common.R;
import com.company.feedback.service.UserService;
import com.company.feedback.vo.UserOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户查询控制器
 *
 * <p>提供下拉框所需的候选人列表。
 * 之所以单独开一个接口而不是把用户列表塞进任务详情里：
 * 候选人列表（比如所有开发）与具体任务是正交的，
 * 缓存一次就能复用，没必要每次看详情都带一遍。
 *
 * @author 反馈改进助手项目组
 */
@Tag(name = "用户查询", description = "下拉框所需的候选人列表")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 按角色查询用户
     *
     * @param role 角色代码，如 DEV；为空返回全部启用用户
     * @return 用户选项列表
     */
    @Operation(summary = "查询用户列表", description = "按角色筛选启用状态的账号")
    @PreAuthorize("hasAnyRole('PM', 'ADMIN', 'DEV')")
    @GetMapping
    public R<List<UserOptionVO>> list(
            @Parameter(description = "角色代码") @RequestParam(required = false) String role) {
        return R.ok(userService.listByRole(role));
    }
}
