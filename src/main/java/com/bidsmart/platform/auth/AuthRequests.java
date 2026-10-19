package com.bidsmart.platform.auth;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() { }
    public static final class Login {
        @NotBlank @Size(max = 128) private String username;
        @NotNull @Size(min = 1, max = 256) private String password;
        @NotBlank @Size(max = 64) private String captchaId;
        @NotBlank @Size(max = 16) private String captchaCode;
        public String getCaptchaId() { return captchaId; }
        public void setCaptchaId(String value) { captchaId = value; }
        public String getCaptchaCode() { return captchaCode; }
        public void setCaptchaCode(String value) { captchaCode = value; }
        public String getUsername() { return username; }
        public void setUsername(String value) { username = value; }
        public String getPassword() { return password; }
        public void setPassword(String value) { password = value; }
    }
    public static final class ChangePassword {
        @NotNull @Size(min = 1, max = 256) private String currentPassword;
        @NotNull @Size(min = 1, max = 256) private String newPassword;
        public String getCurrentPassword() { return currentPassword; }
        public void setCurrentPassword(String value) { currentPassword = value; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String value) { newPassword = value; }
    }
}
