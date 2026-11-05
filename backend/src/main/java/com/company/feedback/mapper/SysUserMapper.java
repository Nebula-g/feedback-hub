package com.company.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.company.feedback.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 系统账号 Mapper
 *
 * <p>继承 MyBatis-Plus 的 BaseMapper 后即拥有完整的单表 CRUD 能力，
 * 无需编写 XML。仅当查询无法用 LambdaQueryWrapper 表达时才在此补充自定义方法。
 *
 * @author 反馈改进助手项目组
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按登录账号查询用户（已排除逻辑删除记录）
     *
     * <p>为什么单独写 SQL 而不用 Wrapper：
     * Spring Security 在认证阶段（早于业务事务）就需要查用户，
     * 直接用注解 SQL 可以减少一层对象构造开销，且语义更清晰。
     *
     * @param username 登录账号
     * @return 用户实体，不存在返回 null
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0 LIMIT 1")
    SysUser selectByUsername(@Param("username") String username);
}
