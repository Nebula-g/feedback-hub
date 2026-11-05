package com.company.feedback.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置类
 *
 * <p>本类注册三个能力：
 * <ol>
 *   <li><b>分页插件</b>：让 Page 对象生效，列表接口统一分页</li>
 *   <li><b>乐观锁插件</b>：让 @Version 注解生效，防止并发覆盖</li>
 *   <li><b>自动填充</b>：create_time / update_time 无需手动 set</li>
 * </ol>
 *
 * <p><b>为什么乐观锁对本项目特别重要</b>：
 * 验收场景里有「两人同时操作同一条记录」的并发测试。
 * 例如两个 PM 同时确认同一张候选卡片，如果没有乐观锁，
 * 会生成两条重复的正式 issue。
 * 加上 @Version 后，后提交的那次会更新 0 行，
 * Service 层检测到后抛出 409 冲突，提示"该记录已被他人处理"。
 *
 * @author 反馈改进助手项目组
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 注册 MyBatis-Plus 拦截器链
     *
     * <p>注意顺序：乐观锁拦截器应放在分页拦截器之前注册，
     * 这样在执行分页查询时版本号更新逻辑仍然生效。
     *
     * @return 拦截器链
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 乐观锁：对带 @Version 字段的实体，UPDATE 时自动追加 version 条件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        // 2. 分页：自动识别 Page 参数，拼接 LIMIT 语句
        PaginationInnerInterceptor paginationInterceptor =
                new PaginationInnerInterceptor(DbType.MYSQL);
        // 单页最大条数，防止恶意请求 ?size=999999 拖垮数据库
        paginationInterceptor.setMaxLimit(500L);
        // 超出总页数时自动回到第一页，而不是返回空列表
        paginationInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }

    /**
     * 注册自动填充处理器
     *
     * <p>作用：实体类上标注了 @TableField(fill = FieldFill.INSERT) 的字段，
     * 在执行 insert / update 时由本处理器自动赋值，
     * 业务代码里不需要（也不应该）手动设置时间字段。
     *
     * @return 自动填充处理器
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {

            /**
             * 插入时填充：创建时间 + 更新时间都赋值
             */
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                // 参数 strictFill=false 表示：字段已有值时不覆盖
                this.strictInsertFill(metaObject, "createTime",
                        LocalDateTime.class, now);
                this.strictInsertFill(metaObject, "updateTime",
                        LocalDateTime.class, now);
            }

            /**
             * 更新时填充：只更新时间
             */
            @Override
            public void updateFill(MetaObject metaObject) {
                this.strictUpdateFill(metaObject, "updateTime",
                        LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
