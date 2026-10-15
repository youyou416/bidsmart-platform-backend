package com.bidsmart.platform.common;

import java.util.Map;

/** Future repositories must supply a fixed, server-owned column map. */
public final class PageRequest {
    private final int page;
    private final int size;
    private final String sortColumn;
    private final boolean descending;

    public PageRequest(Integer page, Integer size, String sort, boolean descending,
                       Map<String, String> permittedColumns) {
        this.page = page == null ? 1 : page;
        this.size = size == null ? 20 : size;
        if (this.page < 1 || this.size < 1 || this.size > 100) {
            throw new IllegalArgumentException("Invalid pagination");
        }
        if (sort != null && !permittedColumns.containsKey(sort)) {
            throw new IllegalArgumentException("Invalid sorting");
        }
        this.sortColumn = sort == null ? null : permittedColumns.get(sort);
        this.descending = descending;
    }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public long getOffset() { return (long) (page - 1) * size; }
    public String getSortColumn() { return sortColumn; }
    public boolean isDescending() { return descending; }
}
