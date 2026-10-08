package io.triform.core;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 统一分页返回:Result.data 为该结构时,约定为服务端分页。
 * 与 MyBatis-Plus Page 的 records/total 字段同名,转换零成本。
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private List<T> records;

    public PageResult() {
    }

    public PageResult(long total, List<T> records) {
        this.total = total;
        this.records = records == null ? Collections.emptyList() : records;
    }

    public static <T> PageResult<T> of(long total, List<T> records) {
        return new PageResult<>(total, records);
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records == null ? Collections.emptyList() : records;
    }
}
