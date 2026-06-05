package com.hrms.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sites", indexes = {
    @Index(name = "idx_site_active", columnList = "active")
})
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site_name", nullable = false)
    private String siteName;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private boolean active = true;

    public Site() {}

    public Site(String siteName, String location) {
        this.siteName = siteName;
        this.location = location;
    }

    public Long getId() { return id; }
    public String getSiteName() { return siteName; }
    public void setSiteName(String siteName) { this.siteName = siteName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
