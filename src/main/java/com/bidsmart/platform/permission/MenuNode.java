package com.bidsmart.platform.permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MenuNode {
    private final Resource resource;
    private final List<MenuNode> children = new ArrayList<>();
    public MenuNode(Resource resource) { this.resource = resource; }
    void addChild(MenuNode child) { children.add(child); }
    public long getId() { return resource.getId(); }
    public Long getParentId() { return resource.getParentId(); }
    public String getName() { return resource.getName(); }
    public Resource.Type getType() { return resource.getType(); }
    public String getRouteName() { return resource.getRouteName(); }
    public String getPath() { return resource.getPath(); }
    public String getComponent() { return resource.getComponent(); }
    public String getIcon() { return resource.getIcon(); }
    public boolean isVisible() { return resource.isVisible(); }
    public boolean isEnabled() { return resource.isEnabled(); }
    public String getPermission() { return resource.getPermission(); }
    public List<MenuNode> getChildren() { return Collections.unmodifiableList(children); }
}
