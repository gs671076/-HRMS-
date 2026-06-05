package com.hrms.dto;

// ── Request DTOs ──────────────────────────────────────────────

public class AttendanceDTOs {

    public record ClockInRequest(Long workerId, Long siteId) {}

    public record ClockOutRequest(Long workerId) {}

    // ── Response DTOs ─────────────────────────────────────────

    public record ClockInResponse(
        Long attendanceId,
        Long workerId,
        String workerName,
        Long siteId,
        String siteName,
        String clockInTime
    ) {}

    public record ClockOutResponse(
        Long attendanceId,
        Long workerId,
        String workerName,
        String clockInTime,
        String clockOutTime,
        double totalHours,
        double overtimeHours,
        boolean flagged
    ) {}

    public record ActiveWorkerEntry(
        String workerId,
        String workerName,
        String siteId,
        String siteName,
        String clockInTime
    ) {}

    public record AttendanceLogEntry(
        Long id,
        String workerName,
        String siteName,
        String clockIn,
        String clockOut,
        double totalHours,
        double overtimeHours,
        boolean flagged
    ) {}

    public record PagedAttendanceResponse(
        java.util.List<AttendanceLogEntry> content,
        long totalElements,
        int totalPages,
        int currentPage,
        int pageSize
    ) {}
}
