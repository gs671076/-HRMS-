package com.hrms.exception;

public class SiteNotFoundException extends RuntimeException {
    public SiteNotFoundException(Long id) {
        super("Site not found or inactive: " + id);
    }
}
