package com.hrms.controller;

import com.hrms.entity.Worker;
import com.hrms.enums.Designation;
import com.hrms.service.WorkerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @GetMapping
    public ResponseEntity<List<Worker>> getAllWorkers() {
        return ResponseEntity.ok(workerService.getAllWorkers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Worker> getWorker(@PathVariable Long id) {
        return ResponseEntity.ok(workerService.getWorker(id));
    }

    @PostMapping
    public ResponseEntity<Worker> createWorker(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(workerService.createWorker(
            (String) body.get("name"),
            (String) body.get("phone"),
            Designation.valueOf((String) body.get("designation")),
            new BigDecimal(body.get("dailyWageRate").toString())
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Worker> updateWorker(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(workerService.updateWorker(
            id,
            (String) body.get("name"),
            (String) body.get("phone"),
            Designation.valueOf((String) body.get("designation")),
            new BigDecimal(body.get("dailyWageRate").toString())
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivateWorker(@PathVariable Long id) {
        workerService.deactivateWorker(id);
        return ResponseEntity.noContent().build();
    }
}
