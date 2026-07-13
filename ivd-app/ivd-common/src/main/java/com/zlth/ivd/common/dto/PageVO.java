package com.zlth.ivd.common.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 统一分页响应 VO
 * <p>
 * 格式：{"total":150, "page":1, "pageSize":10, "records":[...]}
 *
 * @author IVD Team
 * @since 2026-07-01
 */
@Data
@NoArgsConstructor
public class PageVO<T> {

    private long total;
    private long page;
    private long pageSize;
    private List<T> records;

    public PageVO(long total, long page, long pageSize, List<T> records) {
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.records = records != null ? records : Collections.emptyList();
    }

    /**
     * 从 MyBatis-Plus IPage 构建
     */
    public static <T> PageVO<T> of(IPage<T> iPage) {
        return new PageVO<>(
                iPage.getTotal(),
                iPage.getCurrent(),
                iPage.getSize(),
                iPage.getRecords()
        );
    }

    /**
     * 从 MyBatis-Plus IPage 构建，并转换记录类型
     */
    public static <S, T> PageVO<T> of(IPage<S> iPage, Function<S, T> converter) {
        List<T> converted = iPage.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        return new PageVO<>(
                iPage.getTotal(),
                iPage.getCurrent(),
                iPage.getSize(),
                converted
        );
    }

    /**
     * 空分页
     */
    public static <T> PageVO<T> empty(int page, int pageSize) {
        return new PageVO<>(0, page, pageSize, Collections.emptyList());
    }
}
