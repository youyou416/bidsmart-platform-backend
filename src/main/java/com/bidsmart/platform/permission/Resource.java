package com.bidsmart.platform.permission;

public final class Resource {
    public enum Type { DIRECTORY, MENU, BUTTON }
    private final long id;
    private final Long parentId;
    private final String name;
    private final Type type;
    private final int sortOrder;
    private final String routeName;
    private final String path;
    private final String component;
    private final String icon;
    private final boolean visible;
    private final boolean enabled;
    private final String permission;
    public Resource(long id, Long parentId, String name, Type type, int sortOrder, String routeName,
            String path, String component, String icon, boolean visible, boolean enabled, String permission) {
        this.id = id; this.parentId = parentId; this.name = name; this.type = type; this.sortOrder = sortOrder;
        this.routeName = routeName; this.path = path; this.component = component; this.icon = icon;
        this.visible = visible; this.enabled = enabled; this.permission = permission;
    }
    public long getId() { return id; }
    public Long getParentId() { return parentId; }
    public String getName() { return name; }
    public Type getType() { return type; }
    public int getSortOrder() { return sortOrder; }
    public String getRouteName() { return routeName; }
    public String getPath() { return path; }
    public String getComponent() { return component; }
    public String getIcon() { return icon; }
    public boolean isVisible() { return visible; }
    public boolean isEnabled() { return enabled; }
    public String getPermission() { return permission; }
}
