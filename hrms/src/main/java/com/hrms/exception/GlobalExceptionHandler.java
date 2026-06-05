package com.hrms.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> error(String code, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of(
            "error", code,
            "message", message,
            "timestamp", Instant.now().toString()
        ));
    }

    @ExceptionHandler(WorkerNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle(WorkerNotFoundException ex) {
        return error("WORKER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(SiteNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handle(SiteNotFoundException ex) {
        return error("SITE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(DuplicateClockInException.class)
    public ResponseEntity<Map<String, Object>> handle(DuplicateClockInException ex) {
        return error("DUPLICATE_CLOCK_IN", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(WorkerNotClockedInException.class)
    public ResponseEntity<Map<String, Object>> handle(WorkerNotClockedInException ex) {
        return error("WORKER_NOT_CLOCKED_IN", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AlreadySettledException.class)
    public ResponseEntity<Map<String, Object>> handle(AlreadySettledException ex) {
        return error("ALREADY_SETTLED", ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(SettlementCurrentMonthException.class)
    public ResponseEntity<Map<String, Object>> handle(SettlementCurrentMonthException ex) {
        return error("SETTLEMENT_CURRENT_MONTH", ex.getMessage(), HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handle(IllegalArgumentException ex) {
        return error("VALIDATION_ERROR", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return error("INTERNAL_ERROR", "An unexpected error occurred", HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
