package com.company.feedback.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 工具类
 *
 * <p>用途：把 List&lt;String&gt; 之类的集合序列化成字符串存进 TEXT 字段，
 * 以及读取时反序列化回来。
 *
 * <p><b>为什么不用数据库 JSON 类型</b>：
 * 本项目限定 MySQL 5.7+ 兼容，TEXT 存 JSON 字符串是最稳妥的写法。
 * 代价是无法在 SQL 里直接查询 JSON 内部字段，
 * 但本项目的查询条件不涉及图片地址，所以没有实际影响。
 *
 * <p><b>容错原则</b>：所有反序列化失败都返回空集合而不是抛异常。
 * 一张图片地址解析失败，不应该导致整条反馈记录无法显示。
 *
 * @author 反馈改进助手项目组
 */
@Slf4j
public final class JsonUtil {

    /** 共享的 ObjectMapper（Jackson 官方建议复用，线程安全） */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            // 遇到未知字段不报错，避免实体加字段后老数据反序列化失败
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 私有构造，防止实例化 */
    private JsonUtil() {
    }

    /**
     * 对象序列化为 JSON 字符串
     *
     * @param obj 待序列化对象
     * @return JSON 字符串，失败返回 null
     */
    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 字符串列表序列化为 JSON 数组字符串
     *
     * @param list 字符串列表
     * @return JSON 字符串，空列表返回 null（避免库里存 "[]"）
     */
    public static String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return toJson(list);
    }

    /**
     * JSON 数组字符串反序列化为 Long 列表
     *
     * <p>用于解析候选卡片中的 related_feedback_ids 字段。
     *
     * @param json JSON 字符串，如 "[1,2,3]"
     * @return Long 列表，解析失败或为空返回空列表
     */
    public static List<Long> jsonToLongList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Long> list = MAPPER.readValue(json, new TypeReference<List<Long>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("JSON反序列化为Long列表失败，返回空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * JSON 数组字符串反序列化为字符串列表
     *
     * @param json JSON 字符串
     * @return 字符串列表，解析失败或为空返回空列表
     */
    public static List<String> jsonToList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> list = MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            log.warn("JSON反序列化失败，返回空列表: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}
