package com.docsearch.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private String status;       // "success" or "failure"
    private String message;
    private ZonedDateTime timestamp;
    private T data;
    private List<ApiError> errors;

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(message)
                .timestamp(ZonedDateTime.now())
                .data(data)
                .build();
    }

    @SuppressWarnings("unchecked")
    public static <T> ApiResponse<T> failure(String message, List<ApiError> errors) {
        return (ApiResponse<T>) ApiResponse.builder()
                .status("failure")
                .message(message)
                .timestamp(ZonedDateTime.now())
                .errors(errors)
                .build();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiError {
        private String code;
        private String description;
    }
}