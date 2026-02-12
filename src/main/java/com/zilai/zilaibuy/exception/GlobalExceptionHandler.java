package com.zilai.zilaibuy.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
