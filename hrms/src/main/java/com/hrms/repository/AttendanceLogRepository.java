package com.hrms.repository;

import com.hrms.entity.AttendanceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AttendanceLogRepository extends JpaRepository<AttendanceLog, Long> {

    // Check if worker is currently clocked in (no clock-out yet)
    Optional<AttendanceLog> findByWorkerIdAndClockOutIsNull(Long workerId);

    // Paginated history with JOIN FETCH — fixes N+1 (LF-203)
    @EntityGraph(attributePaths = {"worker", "site"})
    @Query("SELECT a FROM AttendanceLog a WHERE a.worker.id = :workerId " +
           "AND a.clockIn >= :from AND a.clockIn <= :to")
    Page<AttendanceLog> findByWorkerAndDateRange(
        @Param("workerId") Long workerId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to,
        Pageable pageable
    );

    // Sum of overtime hours for a worker in a given month (for 60h cap check)
    @Query("SELECT COALESCE(SUM(a.overtimeHours), 0) FROM AttendanceLog a " +
           "WHERE a.worker.id = :workerId " +
           "AND YEAR(a.clockIn) = :year AND MONTH(a.clockIn) = :month " +
           "AND a.clockOut IS NOT NULL")
    java.math.BigDecimal sumOvertimeHoursForMonth(
        @Param("workerId") Long workerId,
        @Param("year") int year,
        @Param("month") int month
    );
}
