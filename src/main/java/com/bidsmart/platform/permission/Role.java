package com.bidsmart.platform.permission;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import com.fasterxml.jackson.annotation.JsonIgnore;

/** Own resource grants are kept separate from dynamically inherited grants. */
public final class Role {
    private final long id;
    private final Long parentId;
    private final String name;
    private final boolean enabled;
    private final Set<Long> ownResourceIds;
    public Role(long id, Long parentId, String name, boolean enabled, Set<Long> ownResourceIds) {
        this.id = id; this.parentId = parentId; this.name = name; this.enabled = enabled;
        this.ownResourceIds = Collections.unmodifiableSet(new LinkedHashSet<>(ownResourceIds));
    }
    public long getId() { return id; }
    @JsonIgnore public Long getParentId() { return parentId; }
    public String getName() { return name; }
    @JsonIgnore public boolean isEnabled() { return enabled; }
    @JsonIgnore public Set<Long> getOwnResourceIds() { return ownResourceIds; }
}
