package com.beibu.mall.product.api.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果包装类
 *
 * 为什么要用这个类？
 * 分页查询需要返回：当前页数据、总记录数、当前页码、每页大小。
 * 如果用 Map<String, Object>，调用方必须知道 key 的名称，容易出错。
 * 用泛型类可以提供类型安全，IDE 也能自动提示。
 *
 * @param <T> 列表中元素的类型
 */
@Data
public class PageResult<T> {

    /** 当前页数据列表 */
    private List<T> list;

    /** 总记录数 */
    private Long total;

    /** 当前页码（从 1 开始） */
    private Integer page;

    /** 每页大小 */
    private Integer size;

    /** 总页数（自动计算） */
    private Integer totalPages;

    public PageResult(List<T> list, Long total, Integer page, Integer size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = (int) Math.ceil((double) total / size);
    }
}
