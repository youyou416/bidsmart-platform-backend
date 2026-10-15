package com.bidsmart.platform.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "platform")
public class PlatformProperties {
    private String environment = "dev";
    private String databasePassword;
    private String databaseUsername = "root@sys";
    private String databaseName = "hr_score_dev";
    private String databaseHost = "127.0.0.1";
    private int databasePort = 2881;
    private String databaseSslMode = "DISABLED";
    private boolean secureCookie;
    private boolean transportVerified;
    private boolean debugEnabled;
    private Duration absoluteSessionTimeout = Duration.ofHours(8);
    private Duration failureWindow = Duration.ofMinutes(15);
    private Duration lockout = Duration.ofMinutes(15);
    private int maxLoginFailures = 5;
    private int maxRequestBodyBytes = 16384;
    private int csrfRequestsPerMinute = 60;
    private int auditRetentionDays = 30;
    private List<String> allowedOrigins = new ArrayList<>(Collections.singletonList("http://127.0.0.1:5173"));
    private boolean bootstrapEnabled;
    private String bootstrapUsername;
    private String bootstrapPassword;
    private long bootstrapRoleId = 1001L;
    private long bootstrapDepartmentId = 1001L;
    private String systemName = "智能化竞价采购平台 · 基础框架";
    private String logoPath = "/mark.svg";
    private String themeColor = "#2563eb";
    private boolean tabsEnabled = true;

    public String getEnvironment() { return environment; }
    public void setEnvironment(String value) { environment = value; }
    public String getDatabasePassword() { return databasePassword; }
    public void setDatabasePassword(String value) { databasePassword = value; }
    public String getDatabaseUsername() { return databaseUsername; }
    public void setDatabaseUsername(String value) { databaseUsername = value; }
    public String getDatabaseName() { return databaseName; }
    public void setDatabaseName(String value) { databaseName = value; }
    public String getDatabaseHost() { return databaseHost; }
    public void setDatabaseHost(String value) { databaseHost = value; }
    public int getDatabasePort() { return databasePort; }
    public void setDatabasePort(int value) { databasePort = value; }
    public String getDatabaseSslMode() { return databaseSslMode; }
    public void setDatabaseSslMode(String value) { databaseSslMode = value; }
    public boolean isSecureCookie() { return secureCookie; }
    public void setSecureCookie(boolean value) { secureCookie = value; }
    public boolean isTransportVerified() { return transportVerified; }
    public void setTransportVerified(boolean value) { transportVerified = value; }
    public boolean isDebugEnabled() { return debugEnabled; }
    public void setDebugEnabled(boolean value) { debugEnabled = value; }
    public Duration getAbsoluteSessionTimeout() { return absoluteSessionTimeout; }
    public void setAbsoluteSessionTimeout(Duration value) { absoluteSessionTimeout = value; }
    public Duration getFailureWindow() { return failureWindow; }
    public void setFailureWindow(Duration value) { failureWindow = value; }
    public Duration getLockout() { return lockout; }
    public void setLockout(Duration value) { lockout = value; }
    public int getMaxLoginFailures() { return maxLoginFailures; }
    public void setMaxLoginFailures(int value) { maxLoginFailures = value; }
    public int getMaxRequestBodyBytes() { return maxRequestBodyBytes; }
    public void setMaxRequestBodyBytes(int value) { maxRequestBodyBytes = value; }
    public int getCsrfRequestsPerMinute() { return csrfRequestsPerMinute; }
    public void setCsrfRequestsPerMinute(int value) { csrfRequestsPerMinute = value; }
    public int getAuditRetentionDays() { return auditRetentionDays; }
    public void setAuditRetentionDays(int value) { auditRetentionDays = value; }
    public List<String> getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(List<String> value) { allowedOrigins = value; }
    public boolean isBootstrapEnabled() { return bootstrapEnabled; }
    public void setBootstrapEnabled(boolean value) { bootstrapEnabled = value; }
    public String getBootstrapUsername() { return bootstrapUsername; }
    public void setBootstrapUsername(String value) { bootstrapUsername = value; }
    public String getBootstrapPassword() { return bootstrapPassword; }
    public void setBootstrapPassword(String value) { bootstrapPassword = value; }
    public long getBootstrapRoleId() { return bootstrapRoleId; }
    public void setBootstrapRoleId(long value) { bootstrapRoleId = value; }
    public long getBootstrapDepartmentId() { return bootstrapDepartmentId; }
    public void setBootstrapDepartmentId(long value) { bootstrapDepartmentId = value; }
    public String getSystemName() { return systemName; }
    public void setSystemName(String value) { systemName = value; }
    public String getLogoPath() { return logoPath; }
    public void setLogoPath(String value) { logoPath = value; }
    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String value) { themeColor = value; }
    public boolean isTabsEnabled() { return tabsEnabled; }
    public void setTabsEnabled(boolean value) { tabsEnabled = value; }
}
