package com.zilai.zilaibuy.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException e, HttpServletRequest req) {
        int code = e.getStatus().value();
        return ResponseEntity.status(e.getStatus())
                .body(new ApiError(Instant.now(), code, e.getStatus().getReasonPhrase(),
                        e.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        return new ApiError(Instant.now(), 403, "Forbidden", e.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleConstraint(ConstraintViolationException e, HttpServletRequest req) {
        return new ApiError(Instant.now(), 400, "Bad Request", e.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleInvalid(MethodArgumentNotValidException e, HttpServletRequest req) {
        return new ApiError(Instant.now(), 400, "Bad Request", e.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleState(IllegalStateException e, HttpServletRequest req) {
        return new ApiError(Instant.now(), 500, "Internal Server Error", e.getMessage(), req.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleAny(Exception e, HttpServletRequest req) {
        return new ApiError(Instant.now(), 500, "Internal Server Error", e.getMessage(), req.getRequestURI());
    }
}
