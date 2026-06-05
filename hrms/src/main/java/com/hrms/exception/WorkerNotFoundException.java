package com.hrms.exception;

public class WorkerNotFoundException extends RuntimeException {
    public WorkerNotFoundException(Long id) {
        super("Worker not found or inactive: " + id);
    }
}
