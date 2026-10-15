package com.bidsmart.platform.common;

public final class ApiResponse<T> {
    private final String code;
    private final String message;
    private final T data;
    private final String traceId;

    public ApiResponse(String code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return new ApiResponse<>("OK", "成功", data, traceId);
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
    public String getTraceId() { return traceId; }
}
