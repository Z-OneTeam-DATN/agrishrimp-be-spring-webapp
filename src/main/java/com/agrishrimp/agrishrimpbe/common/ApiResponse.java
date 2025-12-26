package com.agrishrimp.agrishrimpbe.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;

    // SUCCESS
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "Success", data);
    }

    // FAIL
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
