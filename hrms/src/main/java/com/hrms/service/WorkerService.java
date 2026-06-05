package com.hrms.service;

import com.hrms.entity.Worker;
import com.hrms.enums.Designation;
import com.hrms.exception.WorkerNotFoundException;
import com.hrms.repository.WorkerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WorkerService {

    private final WorkerRepository workerRepo;
    private final AttendanceService attendanceService;

    public WorkerService(WorkerRepository workerRepo, AttendanceService attendanceService) {
        this.workerRepo = workerRepo;
        this.attendanceService = attendanceService;
    }

    public Worker getWorker(Long id) {
        return workerRepo.findById(id)
            .orElseThrow(() -> new WorkerNotFoundException(id));
    }

    public List<Worker> getAllWorkers() {
        return workerRepo.findAll();
    }

    @Transactional
    public Worker createWorker(String name, String phone, Designation designation, BigDecimal dailyWageRate) {
        if (workerRepo.existsByPhone(phone)) {
            throw new IllegalArgumentException("Phone number already registered: " + phone);
        }
        return workerRepo.save(new Worker(name, phone, designation, dailyWageRate));
    }

    @Transactional
    public Worker updateWorker(Long id, String name, String phone, Designation designation, BigDecimal dailyWageRate) {
        Worker worker = workerRepo.findById(id)
            .orElseThrow(() -> new WorkerNotFoundException(id));

        worker.setName(name);
        worker.setPhone(phone);
        worker.setDesignation(designation);
        worker.setDailyWageRate(dailyWageRate);

        Worker saved = workerRepo.save(worker);

        // Cache invalidation: if worker is currently active in Redis,
        // their name/designation/rate may be stale — evict so next GET /active is fresh
        attendanceService.evictWorkerFromRedis(id);

        return saved;
    }

    @Transactional
    public void deactivateWorker(Long id) {
        Worker worker = workerRepo.findById(id)
            .orElseThrow(() -> new WorkerNotFoundException(id));
        worker.setActive(false);
        workerRepo.save(worker);
        attendanceService.evictWorkerFromRedis(id);
    }
}
