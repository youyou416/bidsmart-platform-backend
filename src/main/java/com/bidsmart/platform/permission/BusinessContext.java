package com.bidsmart.platform.permission;

/** Synthetic/adapter context only; no purchase document or business table exists. */
public final class BusinessContext {
    private final Long creatorUserId;
    private final Long declarerUserId;
    private final Long departmentId;
    private final Long enterpriseId;
    private final String purchaseType;
    private final String materialCategory;
    public BusinessContext(Long creatorUserId, Long declarerUserId, Long departmentId, Long enterpriseId,
            String purchaseType, String materialCategory) {
        this.creatorUserId = creatorUserId; this.declarerUserId = declarerUserId;
        this.departmentId = departmentId; this.enterpriseId = enterpriseId;
        this.purchaseType = purchaseType; this.materialCategory = materialCategory;
    }
    public Long getCreatorUserId() { return creatorUserId; }
    public Long getDeclarerUserId() { return declarerUserId; }
    public Long getDepartmentId() { return departmentId; }
    public Long getEnterpriseId() { return enterpriseId; }
    public String getPurchaseType() { return purchaseType; }
    public String getMaterialCategory() { return materialCategory; }
}
