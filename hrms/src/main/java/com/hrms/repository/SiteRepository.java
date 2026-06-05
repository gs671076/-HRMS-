package com.hrms.repository;

import com.hrms.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {
    Optional<Site> findByIdAndActiveTrue(Long id);
}
