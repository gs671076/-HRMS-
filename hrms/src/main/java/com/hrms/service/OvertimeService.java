package com.hrms.service;

import com.hrms.dto.OvertimeDTOs.*;
import com.hrms.entity.OvertimeEntry;
import com.hrms.entity.Worker;
import com.hrms.enums.SettlementStatus;
import com.hrms.event.OvertimeSettledEvent;
import com.hrms.exception.AlreadySettledException;
import com.hrms.exception.SettlementCurrentMonthException;
import com.hrms.exception.WorkerNotFoundException;
import com.hrms.repository.OvertimeEntryRepository;
import com.hrms.repository.WorkerRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

@Service
public class OvertimeService {

    private final OvertimeEntryRepository overtimeRepo;
    private final WorkerRepository workerRepo;
    private final ApplicationEventPublisher eventPublisher;

    public OvertimeService(OvertimeEntryRepository overtimeRepo,
                            WorkerRepository workerRepo,
                            ApplicationEventPublisher eventPublisher) {
        this.overtimeRepo = overtimeRepo;
        this.workerRepo = workerRepo;
        this.eventPublisher = eventPublisher;
    }

    // ─── OVERTIME SUMMARY ─────────────────────────────────────────────────────

    public OvertimeSummaryResponse getSummary(Long workerId, String month) {
        Worker worker = workerRepo.findById(workerId)
            .orElseThrow(() -> new WorkerNotFoundException(workerId));

        YearMonth ym = YearMonth.parse(month);
        List<OvertimeEntry> entries = overtimeRepo.findByWorkerAndMonth(
            workerId, ym.getYear(), ym.getMonthValue());

        double totalOtHours = entries.stream()
            .mapToDouble(e -> e.getOvertimeHours().doubleValue()).sum();

        BigDecimal totalAmount = entries.stream()
            .map(OvertimeEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Overall status: SETTLED if all entries settled, else PENDING
        boolean allSettled = !entries.isEmpty() && entries.stream()
            .allMatch(e -> e.getSettlementStatus() == SettlementStatus.SETTLED);

        List<OvertimeDayEntry> breakdown = entries.stream()
            .map(e -> new OvertimeDayEntry(
                e.getDate().toString(),
                e.getOvertimeHours().doubleValue(),
                e.getAmount(),
                e.getSettlementStatus().name()
            )).toList();

        return new OvertimeSummaryResponse(
            workerId, worker.getName(), month,
            totalOtHours, totalAmount,
            allSettled ? "SETTLED" : "PENDING",
            breakdown
        );
    }

    // ─── SETTLE ───────────────────────────────────────────────────────────────

    /**
     * LF-204: Entire settlement for a worker+month is ONE atomic transaction.
     *
     * Spring proxy trap avoided by keeping this as a public method called
     * from the controller directly — NOT called from another method in this
     * same class (which would bypass the @Transactional proxy).
     *
     * SMS event is published here but fires only AFTER this method returns
     * and the transaction commits — handled by @TransactionalEventListener
     * in SmsNotificationListener.
     *
     * LF-205: External API (min wage rates, etc.) must be fetched BEFORE
     * this method is called — pass results in as parameters. Do NOT make
     * external HTTP calls inside a @Transactional method; it holds a DB
     * connection while waiting on a 3rd-party server.
     */
    @Transactional
    public SettleResponse settle(Long workerId, String month) {
        // Rule: cannot settle current month
        YearMonth ym = YearMonth.parse(month);
        if (ym.equals(YearMonth.now())) {
            throw new SettlementCurrentMonthException();
        }

        Worker worker = workerRepo.findById(workerId)
            .orElseThrow(() -> new WorkerNotFoundException(workerId));

        List<OvertimeEntry> pendingEntries = overtimeRepo.findPendingByWorkerAndMonth(
            workerId, ym.getYear(), ym.getMonthValue());

        // Rule: if nothing pending, check if already settled
        if (pendingEntries.isEmpty()) {
            throw new AlreadySettledException(month);
        }

        // LF-204: Settle ALL entries atomically — if any fails, all roll back
        for (OvertimeEntry entry : pendingEntries) {
            entry.setSettlementStatus(SettlementStatus.SETTLED);
        }
        overtimeRepo.saveAll(pendingEntries);

        BigDecimal totalAmount = pendingEntries.stream()
            .map(OvertimeEntry::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // LF-204: Publish event — SMS fires AFTER transaction commits (AFTER_COMMIT phase)
        // If DB rolls back, this event is never delivered to the listener.
        eventPublisher.publishEvent(new OvertimeSettledEvent(
            workerId, worker.getName(), worker.getPhone(), month, totalAmount));

        return new SettleResponse(
            workerId, worker.getName(), month,
            pendingEntries.size(), totalAmount, "SETTLED"
        );
    }
}
