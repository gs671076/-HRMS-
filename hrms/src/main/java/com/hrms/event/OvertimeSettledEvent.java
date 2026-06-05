package com.hrms.event;

import java.math.BigDecimal;

/**
 * LF-204: Published AFTER the settlement transaction completes.
 * OvertimeService publishes this; SmsNotificationListener consumes it.
 * Decoupled so DB state is always correct even if SMS fails.
 */
public class OvertimeSettledEvent {

    private final Long workerId;
    private final String workerName;
    private final String workerPhone;
    private final String month;        // e.g. "2026-03"
    private final BigDecimal totalAmount;

    public OvertimeSettledEvent(Long workerId, String workerName, String workerPhone,
                                 String month, BigDecimal totalAmount) {
        this.workerId = workerId;
        this.workerName = workerName;
        this.workerPhone = workerPhone;
        this.month = month;
        this.totalAmount = totalAmount;
    }

    public Long getWorkerId() { return workerId; }
    public String getWorkerName() { return workerName; }
    public String getWorkerPhone() { return workerPhone; }
    public String getMonth() { return month; }
    public BigDecimal getTotalAmount() { return totalAmount; }
}
