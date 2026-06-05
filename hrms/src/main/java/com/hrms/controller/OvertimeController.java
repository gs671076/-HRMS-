package com.hrms.controller;

import com.hrms.dto.OvertimeDTOs.*;
import com.hrms.service.OvertimeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/overtime")
public class OvertimeController {

    private final OvertimeService overtimeService;

    public OvertimeController(OvertimeService overtimeService) {
        this.overtimeService = overtimeService;
    }

    /**
     * Monthly overtime summary: total hours, day-by-day breakdown,
     * total payout amount, settlement status.
     * @param month format: YYYY-MM  e.g. 2026-03
     */
    @GetMapping("/summary/{workerId}")
    public ResponseEntity<OvertimeSummaryResponse> getSummary(
        @PathVariable Long workerId,
        @RequestParam String month
    ) {
        return ResponseEntity.ok(overtimeService.getSummary(workerId, month));
    }

    /**
     * Settle all PENDING overtime entries for a worker+month.
     * LF-204: atomic — all or nothing. Cannot settle current month.
     * SMS sent via @TransactionalEventListener(AFTER_COMMIT).
     * @param month format: YYYY-MM  e.g. 2026-03
     */
    @PostMapping("/settle/{workerId}")
    public ResponseEntity<SettleResponse> settle(
        @PathVariable Long workerId,
        @RequestParam String month
    ) {
        return ResponseEntity.ok(overtimeService.settle(workerId, month));
    }
}
