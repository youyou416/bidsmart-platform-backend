package com.bidsmart.platform.permission;

public final class Department {
    private final long id;
    private final Long parentId;
    private final boolean enabled;
    public Department(long id, Long parentId, boolean enabled) {
        this.id = id; this.parentId = parentId; this.enabled = enabled;
    }
    public long getId() { return id; }
    public Long getParentId() { return parentId; }
    public boolean isEnabled() { return enabled; }
}
