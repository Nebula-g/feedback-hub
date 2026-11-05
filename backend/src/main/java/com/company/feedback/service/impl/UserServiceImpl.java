package com.company.feedback.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.feedback.entity.SysUser;
import com.company.feedback.mapper.SysUserMapper;
import com.company.feedback.service.UserService;
import com.company.feedback.vo.UserOptionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 用户查询服务实现
 *
 * @author 反馈改进助手项目组
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;

    /**
     * 按角色查询启用的用户
     *
     * <p>只返回 status=1（启用）且未逻辑删除的账号。
     * 这样即便数据库里保留了离职人员的历史记录，
     * 也不会出现在「指派负责人」的候选列表里。
     *
     * @param role 角色代码，为空时返回全部启用用户
     * @return 用户选项列表
     */
    @Override
    public List<UserOptionVO> listByRole(String role) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getStatus, 1)
                .eq(StringUtils.hasText(role), SysUser::getRole, role)
                .orderByAsc(SysUser::getId);

        return sysUserMapper.selectList(wrapper).stream()
                .map(this::toOption)
                .toList();
    }

    /**
     * 实体转选项视图对象
     *
     * @param user 用户实体
     * @return 用户选项
     */
    private UserOptionVO toOption(SysUser user) {
        return UserOptionVO.builder()
                .id(user.getId())
                .displayName(user.getDisplayName())
                .username(user.getUsername())
                .dept(user.getDept())
                .build();
    }
}
