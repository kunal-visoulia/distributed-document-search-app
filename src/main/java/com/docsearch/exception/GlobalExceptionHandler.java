package com.docsearch.exception;

import com.docsearch.dto.ApiResponse;
import com.docsearch.dto.ApiResponse.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(DocumentNotFoundException ex) {
        return respond(HttpStatus.NOT_FOUND, ex.getMessage(), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> ApiError.builder()
                        .code(ErrorCode.VALIDATION_ERROR.getCode())
                        .description(e.getField() + ": " + e.getDefaultMessage())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.failure("Validation failed", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", ErrorCode.INTERNAL_ERROR);
    }

    private ResponseEntity<ApiResponse<?>> respond(HttpStatus status, String message, ErrorCode errorCode) {
        return ResponseEntity.status(status).body(
                ApiResponse.failure(message, List.of(
                        ApiError.builder()
                                .code(errorCode.getCode())
                                .description(errorCode.getDescription())
                                .build()
                )));
    }
}