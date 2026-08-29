package com.entloom.e5.statictest.fixture;

/** 客户档案查询条件，避免由实体默认值隐式推断过滤语义。 */
public final class CustomerProfileQuery {
    /** 客户展示名称，空值表示不按名称过滤。 */
    private final String displayName;
    /** 页码，从 1 开始。 */
    private final int pageNumber;
    /** 每页记录数。 */
    private final int pageSize;

    public CustomerProfileQuery(String displayName, int pageNumber, int pageSize) {
        this.displayName = displayName;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
    }

    public String getDisplayName() { return displayName; }
    public int getPageNumber() { return pageNumber; }
    public int getPageSize() { return pageSize; }
}
