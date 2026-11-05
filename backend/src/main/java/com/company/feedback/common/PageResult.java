package com.company.feedback.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * 统一分页响应结构
 *
 * <p>为什么不用 MyBatis-Plus 的 IPage 直接返回：
 * IPage 里包含大量内部字段（如 optimizeCountSql、searchCount 等），
 * 直接序列化会给前端带来噪音，也暴露了持久层实现细节。
 * 这里做一层转换，只暴露前端真正需要的四个字段。
 *
 * @param <T> 列表元素类型
 * @author 反馈改进助手项目组
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应")
public class PageResult<T> {

    /** 当前页码，从 1 开始 */
    @Schema(description = "当前页码", example = "1")
    private Long pageNo;

    /** 每页条数 */
    @Schema(description = "每页条数", example = "10")
    private Long pageSize;

    /** 总记录数 */
    @Schema(description = "总记录数", example = "60")
    private Long total;

    /** 当前页数据 */
    @Schema(description = "当前页数据")
    private List<T> records;

    /**
     * 从 MyBatis-Plus 的 IPage 转换为本结构（不做元素类型转换）
     *
     * @param page 分页对象
     * @param <T>  元素类型
     * @return 分页响应
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords() == null ? Collections.emptyList() : page.getRecords()
        );
    }

    /**
     * 从 MyBatis-Plus 的 IPage 转换，并把实体转换为 VO
     *
     * <p>典型用法：数据库查出 Entity 分页，转成前端需要的 VO 分页。
     *
     * @param page     分页对象
     * @param converter 实体到 VO 的转换函数
     * @param <E>      实体类型
     * @param <V>      VO 类型
     * @return 分页响应
     */
    public static <E, V> PageResult<V> of(IPage<E> page,
                                          Function<E, V> converter) {
        List<V> records = page.getRecords() == null
                ? Collections.emptyList()
                : page.getRecords().stream().map(converter).toList();
        return new PageResult<>(page.getCurrent(), page.getSize(),
                page.getTotal(), records);
    }
}
