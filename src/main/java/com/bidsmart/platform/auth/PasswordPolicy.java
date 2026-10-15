package com.bidsmart.platform.auth;

import com.bidsmart.platform.common.ApiException;
import org.springframework.http.HttpStatus;

public final class PasswordPolicy {
    private PasswordPolicy() { }
    public static void validate(String password) {
        if (password == null) { throw invalid(); }
        int length = password.codePointCount(0, password.length());
        if (length < 12 || length > 128 || password.indexOf('\0') >= 0) { throw invalid(); }
        // Reject unpaired UTF-16 surrogates; preserve spaces and every valid Unicode code point.
        for (int i = 0; i < password.length(); i++) {
            char character = password.charAt(i);
            if (Character.isHighSurrogate(character)) {
                if (i + 1 >= password.length() || !Character.isLowSurrogate(password.charAt(++i))) { throw invalid(); }
            } else if (Character.isLowSurrogate(character)) { throw invalid(); }
        }
    }
    private static ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "密码需为 12 至 128 个字符");
    }
}
