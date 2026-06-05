package com.hrms.exception;

public class DuplicateClockInException extends RuntimeException {
    public DuplicateClockInException(String siteName) {
        super("Worker is already clocked in at Site: " + siteName);
    }
}
