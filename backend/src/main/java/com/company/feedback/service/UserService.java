package com.company.feedback.service;

import com.company.feedback.vo.UserOptionVO;

import java.util.List;

/**
 * 用户查询服务
 *
 * <p>本服务只提供<b>只读</b>的用户查询能力，
 * 不提供新增、修改、删除——账号由系统管理员在数据库中维护，
 * 业务系统不开放账号写入口，避免权限被绕过。
 *
 * @author 反馈改进助手项目组
 */
public interface UserService {

    /**
     * 按角色查询可选用户
     *
     * <p>当前用于「指派任务负责人」的下拉框：
     * 只返回指定角色且启用状态的用户。
     *
     * @param role 角色代码，如 DEV / PM / CS；为空时返回全部启用用户
     * @return 用户选项列表（仅含 ID、姓名、账号、部门）
     */
    List<UserOptionVO> listByRole(String role);
}
