package com.tripagent.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 分页响应数据。
 */
@Data
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private long pageNum;
    private long pageSize;
}
