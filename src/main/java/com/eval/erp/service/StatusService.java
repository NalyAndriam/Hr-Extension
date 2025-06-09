package com.eval.erp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class StatusService {

    private static final Logger logger = LoggerFactory.getLogger(StatusService.class);

    public List<String> getAllStatuses() {
        try {
            return Arrays.asList("Active", "Inactive", "Suspended", "Left");
        } catch (Exception e) {
            logger.error("Error fetching statuses: {}", e.getMessage());
            throw new RuntimeException("Error fetching statuses: " + e.getMessage());
        }
    }
}