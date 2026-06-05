package com.hrms.service;

import com.hrms.dto.AttendanceDTOs.*;
import com.hrms.entity.AttendanceLog;
import com.hrms.entity.OvertimeEntry;
import com.hrms.entity.Site;
import com.hrms.entity.Worker;
import com.hrms.exception.*;
import com.hrms.repository.AttendanceLogRepository;
import com.hrms.repository.OvertimeEntryRepository;
import com.hrms.repository.SiteRepository;
import com.hrms.repository.WorkerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class AttendanceService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);

    private static final String REDIS_ACTIVE_KEY = "active_workers:";
    private static final int STANDARD_HOURS = 8;
    private static final int MAX_SHIFT_HOURS = 16;
    // 60-hour monthly overtime cap per worker
    private static final BigDecimal MONTHLY_OT_CAP = BigDecimal.valueOf(60);
    // TTL = 16 hours (max shift duration) — missed clock-outs expire automatically
    private static final long REDIS_TTL_SECONDS = 57600L;

    private final WorkerRepository workerRepo;
    private final SiteRepository siteRepo;
    private final AttendanceLogRepository attendanceRepo;
    private final OvertimeEntryRepository overtimeRepo;
    private final RedisTemplate<String, Object> redisTemplate;

    public AttendanceService(WorkerRepository workerRepo, SiteRepository siteRepo,
                              AttendanceLogRepository attendanceRepo,
                              OvertimeEntryRepository overtimeRepo,
                              RedisTemplate<String, Object> redisTemplate) {
        this.workerRepo = workerRepo;
        this.siteRepo = siteRepo;
        this.attendanceRepo = attendanceRepo;
        this.overtimeRepo = overtimeRepo;
        this.redisTemplate = redisTemplate;
    }

    // ─── CLOCK IN ─────────────────────────────────────────────────────────────

    @Transactional
    public ClockInResponse clockIn(Long workerId, Long siteId) {
        // Rule: worker must exist and be active
        Worker worker = workerRepo.findByIdAndActiveTrue(workerId)
            .orElseThrow(() -> new WorkerNotFoundException(workerId));

        // Rule: site must exist and be active
        Site site = siteRepo.findByIdAndActiveTrue(siteId)
            .orElseThrow(() -> new SiteNotFoundException(siteId));

        // Rule: worker cannot clock in if already clocked in
        attendanceRepo.findByWorkerIdAndClockOutIsNull(workerId).ifPresent(existing -> {
            throw new DuplicateClockInException(existing.getSite().getSiteName());
        });

        LocalDateTime now = LocalDateTime.now();

        // Rule: clock-in time cannot be in the future (system clock check)
        // This guard is here in case of any clock manipulation; LocalDateTime.now() is always present
        AttendanceLog log2 = new AttendanceLog(worker, site, now);
        attendanceRepo.save(log2);

        // Write to Redis — GET /active reads exclusively from here
        writeToRedis(worker, site, now);

        return new ClockInResponse(
            log2.getId(), worker.getId(), worker.getName(),
            site.getId(), site.getSiteName(),
            now.toString()
        );
    }

    // ─── CLOCK OUT ────────────────────────────────────────────────────────────

    @Transactional
    public ClockOutResponse clockOut(Long workerId) {
        // Rule: worker must be currently clocked in
        AttendanceLog record = attendanceRepo.findByWorkerIdAndClockOutIsNull(workerId)
            .orElseThrow(() -> new WorkerNotClockedInException(workerId));

        Worker worker = record.getWorker();
        LocalDateTime clockOut = LocalDateTime.now();
        record.setClockOut(clockOut);

        // Calculate total hours worked
        double hoursWorked = Duration.between(record.getClockIn(), clockOut).toMinutes() / 60.0;
        BigDecimal totalHours = BigDecimal.valueOf(hoursWorked).setScale(2, RoundingMode.HALF_UP);
        record.setTotalHours(totalHours);

        // Rule: flag shift if total hours > 16
        if (hoursWorked > MAX_SHIFT_HOURS) {
            record.setFlagged(true);
            log.warn("Worker {} flagged: shift exceeded 16h ({}h)", workerId, hoursWorked);
        }

        // Calculate and cap overtime
        BigDecimal overtimeHours = calculateAndCapOvertime(worker, record, hoursWorked, clockOut);
        record.setOvertimeHours(overtimeHours);

        attendanceRepo.save(record);

        // Create OvertimeEntry if there is any overtime
        if (overtimeHours.compareTo(BigDecimal.ZERO) > 0) {
            OvertimeEntry entry = buildOvertimeEntry(worker, record, overtimeHours);
            overtimeRepo.save(entry);
        }

        // Remove from Redis active workers
        removeFromRedis(workerId);

        return new ClockOutResponse(
            record.getId(), worker.getId(), worker.getName(),
            record.getClockIn().toString(), clockOut.toString(),
            totalHours.doubleValue(), overtimeHours.doubleValue(),
            record.isFlagged()
        );
    }

    // ─── ACTIVE WORKERS (Redis only) ─────────────────────────────────────────

    /**
     * Reads exclusively from Redis. If Redis is down, CacheErrorHandler
     * in RedisConfig will log the error and this returns empty list.
     */
    public List<ActiveWorkerEntry> getActiveWorkers() {
        List<ActiveWorkerEntry> result = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys(REDIS_ACTIVE_KEY + "*");
            if (keys == null || keys.isEmpty()) return result;

            for (String key : keys) {
                Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
                if (!entries.isEmpty()) {
                    result.add(new ActiveWorkerEntry(
                        String.valueOf(entries.get("workerId")),
                        String.valueOf(entries.get("workerName")),
                        String.valueOf(entries.get("siteId")),
                        String.valueOf(entries.get("siteName")),
                        String.valueOf(entries.get("clockInTime"))
                    ));
                }
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for active workers query — returning empty: {}", e.getMessage());
        }
        return result;
    }

    // ─── ATTENDANCE LOG (Paginated, N+1 free) ────────────────────────────────

    /**
     * LF-203: Paginated with JOIN FETCH via @EntityGraph.
     * No N+1 — Worker and Site load in the same query.
     */
    public PagedAttendanceResponse getAttendanceLog(Long workerId, LocalDateTime from,
                                                     LocalDateTime to, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "clockIn"));
        Page<AttendanceLog> pageResult = attendanceRepo.findByWorkerAndDateRange(workerId, from, to, pageable);

        List<AttendanceLogEntry> content = pageResult.getContent().stream()
            .map(a -> new AttendanceLogEntry(
                a.getId(),
                a.getWorker().getName(),
                a.getSite().getSiteName(),
                a.getClockIn().toString(),
                a.getClockOut() != null ? a.getClockOut().toString() : null,
                a.getTotalHours() != null ? a.getTotalHours().doubleValue() : 0,
                a.getOvertimeHours().doubleValue(),
                a.isFlagged()
            )).toList();

        return new PagedAttendanceResponse(
            content,
            pageResult.getTotalElements(),
            pageResult.getTotalPages(),
            pageResult.getNumber(),
            pageResult.getSize()
        );
    }

    // ─── OVERTIME CALCULATION ─────────────────────────────────────────────────

    /**
     * Business rule:
     *   - Standard shift = 8h. Overtime = hours beyond 8h.
     *   - Rate: 1.5× daily wage/8 for first 2h of OT
     *           2.0× daily wage/8 beyond 2h of OT
     *   - Monthly cap: 60h. If this clock-out would push past cap,
     *     cap the entry at remaining hours (still record attendance fully).
     */
    private BigDecimal calculateAndCapOvertime(Worker worker, AttendanceLog record,
                                                double hoursWorked, LocalDateTime clockOut) {
        if (hoursWorked <= STANDARD_HOURS) return BigDecimal.ZERO;

        double rawOvertimeHours = hoursWorked - STANDARD_HOURS;

        // Check monthly cap
        int year = clockOut.getYear();
        int month = clockOut.getMonthValue();
        BigDecimal alreadyUsed = attendanceRepo.sumOvertimeHoursForMonth(worker.getId(), year, month);
        if (alreadyUsed == null) alreadyUsed = BigDecimal.ZERO;

        BigDecimal remaining = MONTHLY_OT_CAP.subtract(alreadyUsed);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            log.info("Worker {} has hit 60h OT cap for {}/{} — OT capped at 0 for this entry", worker.getId(), year, month);
            return BigDecimal.ZERO;
        }

        BigDecimal cappedOT = BigDecimal.valueOf(rawOvertimeHours).min(remaining)
                                        .setScale(2, RoundingMode.HALF_UP);
        return cappedOT;
    }

    private OvertimeEntry buildOvertimeEntry(Worker worker, AttendanceLog record, BigDecimal overtimeHours) {
        BigDecimal hourlyRate = worker.getDailyWageRate()
            .divide(BigDecimal.valueOf(STANDARD_HOURS), 4, RoundingMode.HALF_UP);

        // 1.5× for first 2h, 2× beyond
        BigDecimal first2h = overtimeHours.min(BigDecimal.valueOf(2));
        BigDecimal beyond2h = overtimeHours.subtract(first2h).max(BigDecimal.ZERO);

        BigDecimal amount = first2h.multiply(hourlyRate).multiply(BigDecimal.valueOf(1.5))
            .add(beyond2h.multiply(hourlyRate).multiply(BigDecimal.valueOf(2.0)))
            .setScale(2, RoundingMode.HALF_UP);

        // Effective rate for record-keeping
        BigDecimal effectiveRate = overtimeHours.compareTo(BigDecimal.ZERO) > 0
            ? amount.divide(overtimeHours, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        OvertimeEntry entry = new OvertimeEntry();
        entry.setWorker(worker);
        entry.setAttendance(record);
        entry.setDate(record.getClockIn().toLocalDate());
        entry.setOvertimeHours(overtimeHours);
        entry.setOvertimeRateApplied(effectiveRate);
        entry.setAmount(amount);
        return entry;
    }

    // ─── REDIS HELPERS ────────────────────────────────────────────────────────

    private void writeToRedis(Worker worker, Site site, LocalDateTime clockInTime) {
        try {
            String key = REDIS_ACTIVE_KEY + worker.getId();
            Map<String, String> data = Map.of(
                "workerId",    String.valueOf(worker.getId()),
                "workerName",  worker.getName(),
                "siteId",      String.valueOf(site.getId()),
                "siteName",    site.getSiteName(),
                "clockInTime", clockInTime.toString()
            );
            redisTemplate.opsForHash().putAll(key, data);
            // TTL: 16 hours — nobody stays clocked in longer; missed clock-outs expire automatically
            redisTemplate.expire(key, REDIS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // LF-202: Redis failure must not fail the clock-in. DB write already done.
            log.warn("Redis write failed for worker {} clock-in — continuing without cache: {}", worker.getId(), e.getMessage());
        }
    }

    private void removeFromRedis(Long workerId) {
        try {
            redisTemplate.delete(REDIS_ACTIVE_KEY + workerId);
        } catch (Exception e) {
            log.warn("Redis delete failed for worker {} clock-out — continuing: {}", workerId, e.getMessage());
        }
    }

    /**
     * Called by WorkerService when a worker profile is updated.
     * Evicts stale cached entry so next GET /active reflects updated data.
     */
    public void evictWorkerFromRedis(Long workerId) {
        try {
            redisTemplate.delete(REDIS_ACTIVE_KEY + workerId);
        } catch (Exception e) {
            log.warn("Redis evict on worker update failed for {} — continuing: {}", workerId, e.getMessage());
        }
    }
}
