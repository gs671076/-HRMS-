package com.hrms.controller;

import com.hrms.dto.AttendanceDTOs.*;
import com.hrms.service.AttendanceService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/clock-in")
    public ResponseEntity<ClockInResponse> clockIn(@RequestBody ClockInRequest request) {
        return ResponseEntity.ok(attendanceService.clockIn(request.workerId(), request.siteId()));
    }

    @PostMapping("/clock-out")
    public ResponseEntity<ClockOutResponse> clockOut(@RequestBody ClockOutRequest request) {
        return ResponseEntity.ok(attendanceService.clockOut(request.workerId()));
    }

    /**
     * Served exclusively from Redis — not the database.
     * Fast path for site supervisors checking who's on-site.
     */
    @GetMapping("/active")
    public ResponseEntity<List<ActiveWorkerEntry>> getActiveWorkers() {
        return ResponseEntity.ok(attendanceService.getActiveWorkers());
    }

    /**
     * LF-203: Paginated attendance log with N+1 fix via @EntityGraph JOIN FETCH.
     * Default: page=0, size=20, sorted by clock-in DESC.
     */
    @GetMapping("/log")
    public ResponseEntity<PagedAttendanceResponse> getAttendanceLog(
        @RequestParam Long workerId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);
        return ResponseEntity.ok(attendanceService.getAttendanceLog(workerId, fromDt, toDt, page, size));
    }
}
