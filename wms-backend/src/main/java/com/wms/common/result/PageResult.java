package com.wms.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private long current;
    private long size;
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> page = new PageResult<>();
        page.setRecords(records);
        page.setTotal(total);
        page.setCurrent(current);
        page.setSize(size);
        page.setPages(size == 0 ? 0 : (total + size - 1) / size);
        return page;
    }
}
