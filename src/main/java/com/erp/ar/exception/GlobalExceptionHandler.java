package com.erp.ar.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Object> handleNotFound(ResourceNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<Object> handleInvalidState(InvalidStateTransitionException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SegregationOfDutiesViolationException.class)
    public ResponseEntity<Object> handleSoD(SegregationOfDutiesViolationException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(UnbalancedJournalEntryException.class)
    public ResponseEntity<Object> handleUnbalanced(UnbalancedJournalEntryException ex) {
        return body(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT,
            "This record was modified by another request. Please re-fetch and retry.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<Object> body(HttpStatus status, String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("timestamp", Instant.now().toString());
        map.put("status", status.value());
        map.put("error", status.getReasonPhrase());
        map.put("message", message);
        return ResponseEntity.status(status).body(map);
    }
}
