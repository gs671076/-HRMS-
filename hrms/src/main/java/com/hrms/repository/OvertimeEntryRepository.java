package com.hrms.repository;

import com.hrms.entity.OvertimeEntry;
import com.hrms.enums.SettlementStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface OvertimeEntryRepository extends JpaRepository<OvertimeEntry, Long> {

    // All entries for a worker in a month (for summary + settle)
    @EntityGraph(attributePaths = {"worker", "attendance"})
    @Query("SELECT o FROM OvertimeEntry o WHERE o.worker.id = :workerId " +
           "AND YEAR(o.date) = :year AND MONTH(o.date) = :month")
    List<OvertimeEntry> findByWorkerAndMonth(
        @Param("workerId") Long workerId,
        @Param("year") int year,
        @Param("month") int month
    );

    // Only PENDING entries for settlement
    @Query("SELECT o FROM OvertimeEntry o WHERE o.worker.id = :workerId " +
           "AND YEAR(o.date) = :year AND MONTH(o.date) = :month " +
           "AND o.settlementStatus = 'PENDING'")
    List<OvertimeEntry> findPendingByWorkerAndMonth(
        @Param("workerId") Long workerId,
        @Param("year") int year,
        @Param("month") int month
    );

    // Total amount of SETTLED entries for a worker+month (for response)
    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM OvertimeEntry o " +
           "WHERE o.worker.id = :workerId " +
           "AND YEAR(o.date) = :year AND MONTH(o.date) = :month " +
           "AND o.settlementStatus = 'SETTLED'")
    BigDecimal sumSettledAmountForMonth(
        @Param("workerId") Long workerId,
        @Param("year") int year,
        @Param("month") int month
    );

    boolean existsByWorkerIdAndSettlementStatus(Long workerId, SettlementStatus status);
}
