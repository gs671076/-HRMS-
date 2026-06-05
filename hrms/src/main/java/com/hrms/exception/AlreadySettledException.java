package com.hrms.exception;

public class AlreadySettledException extends RuntimeException {
    public AlreadySettledException(String month) {
        super("Overtime for " + month + " is already fully settled");
    }
}
