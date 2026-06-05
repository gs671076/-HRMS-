package com.hrms.exception;

public class WorkerNotClockedInException extends RuntimeException {
    public WorkerNotClockedInException(Long workerId) {
        super("Worker is not currently clocked in: " + workerId);
    }
}
