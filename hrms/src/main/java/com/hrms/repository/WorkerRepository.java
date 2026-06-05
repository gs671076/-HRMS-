package com.hrms.repository;

import com.hrms.entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {
    Optional<Worker> findByIdAndActiveTrue(Long id);
    boolean existsByPhone(String phone);
}
