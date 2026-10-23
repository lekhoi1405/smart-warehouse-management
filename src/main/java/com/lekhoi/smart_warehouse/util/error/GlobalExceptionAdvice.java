package com.lekhoi.smart_warehouse.util.error;

import java.util.List;

import com.lekhoi.smart_warehouse.domain.dto.response.RestResponse;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.AlreadyExistsException;
import com.lekhoi.smart_warehouse.util.error.ExceptionCustom.IdInvalidException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@ControllerAdvice
public class GlobalExceptionAdvice {

    private ResponseEntity<RestResponse<Object>> error(
            HttpStatus status,
            String error,
            Object message) {

        RestResponse<Object> response = new RestResponse<>();
        response.setStatusCode(status.value());
        response.setError(error);
        response.setMessage(message);

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(IdInvalidException.class)
    public ResponseEntity<RestResponse<Object>> handleNotFound(
            IdInvalidException ex) {

        return error(
            HttpStatus.NOT_FOUND,
            "Not found",
            ex.getMessage()
        );
    }

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<RestResponse<Object>> handleConflict(
            AlreadyExistsException ex) {

        return error(
            HttpStatus.CONFLICT,
            "Conflict",
            ex.getMessage()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .toList();

        return error(
            HttpStatus.BAD_REQUEST,
            "Validation failed",
            errors
        );
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<RestResponse<Object>> handleBadRequest(
            Exception ex) {

        return error(
            HttpStatus.BAD_REQUEST,
            "Bad request",
            "Invalid request parameter or body"
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RestResponse<Object>> handleIntegrity(
            DataIntegrityViolationException ex) {

        return error(
            HttpStatus.CONFLICT,
            "Data integrity violation",
            "Database constraint violated"
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleServerError(Exception ex) {
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal server error",
            "Unexpected server error"
        );
    }

    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<RestResponse<Object>> handleInvalidState(IllegalStateException ex) {
        return error(HttpStatus.CONFLICT, "Invalid state transition", ex.getMessage());
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RestResponse<Object>> handleAuthenticationError(AuthenticationException ex) {
        return error(HttpStatus.UNAUTHORIZED, "Unauthorized", "Invalid credentials or inactive account");
    }

}
