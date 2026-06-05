package com.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

public class OvertimeDTOs {

    public record OvertimeDayEntry(
        String date,
        double overtimeHours,
        BigDecimal amount,
        String settlementStatus
    ) {}

    public record OvertimeSummaryResponse(
        Long workerId,
        String workerName,
        String month,
        double totalOvertimeHours,
        BigDecimal totalPayoutAmount,
        String overallSettlementStatus,
        List<OvertimeDayEntry> breakdown
    ) {}

    public record SettleResponse(
        Long workerId,
        String workerName,
        String month,
        int entriesSettled,
        BigDecimal totalAmount,
        String status
    ) {}
}
