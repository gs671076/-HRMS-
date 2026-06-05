package com.hrms.exception;

public class SettlementCurrentMonthException extends RuntimeException {
    public SettlementCurrentMonthException() {
        super("Cannot settle overtime for the current month. Only past months can be settled.");
    }
}
