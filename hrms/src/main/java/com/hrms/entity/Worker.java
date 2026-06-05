package com.hrms.entity;

import com.hrms.enums.Designation;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "workers", indexes = {
    @Index(name = "idx_worker_active", columnList = "active")
})
public class Worker {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Designation designation;

    @Column(name = "daily_wage_rate", nullable = false, precision = 10, scale = 2)
    private BigDecimal dailyWageRate;

    @Column(nullable = false)
    private boolean active = true;

    public Worker() {}

    public Worker(String name, String phone, Designation designation, BigDecimal dailyWageRate) {
        this.name = name;
        this.phone = phone;
        this.designation = designation;
        this.dailyWageRate = dailyWageRate;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Designation getDesignation() { return designation; }
    public void setDesignation(Designation designation) { this.designation = designation; }
    public BigDecimal getDailyWageRate() { return dailyWageRate; }
    public void setDailyWageRate(BigDecimal dailyWageRate) { this.dailyWageRate = dailyWageRate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
