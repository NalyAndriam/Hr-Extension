package com.eval.erp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class ResetDataService {

    private static final Logger logger = LoggerFactory.getLogger(ResetDataService.class);

    @Autowired
    private ErpNextApiService erpNextApiService;

    @Autowired
    private HttpSession session;

    private static final List<String> PROTECTED_SALARY_COMPONENTS = Arrays.asList(
        "Leave Encashment", "Arrear", "Basic", "Income Tax"
    );

    public List<String> resetData(String sid) throws Exception {
        session.removeAttribute("employeeRefToNameMap");
        logger.info("Cleared employeeRefToNameMap from session");

        List<String> results = new ArrayList<>();
        logger.info("Starting data reset process");

        // Define doctypes in dependency-aware order
        String[] doctypes = {
            "Salary Slip",
            "Salary Structure Assignment",
            "Salary Structure",
            "Employee",
            "Salary Component" // Correct DocType name
        };

        for (String doctype : doctypes) {
            try {
                // Fetch all records for the doctype
                String fields = "[\"name\", \"docstatus\"]"; // Include docstatus to check state
                String filters = doctype.equals("Salary Component")
                    ? "[[\"name\",\"not in\",\"" + String.join("\",\"", PROTECTED_SALARY_COMPONENTS) + "\"]]"
                    : "";
                ResponseEntity<Map> response = erpNextApiService.getResource(doctype, fields, filters, sid);
                List<Map<String, Object>> records = (List<Map<String, Object>>) response.getBody().get("data");

                if (records.isEmpty()) {
                    results.add(String.format("No records found in %s to delete", doctype));
                    logger.info("No records found in {} to delete", doctype);
                    continue;
                }

                // Process each record
                for (Map<String, Object> record : records) {
                    String recordName = (String) record.get("name");
                    Integer docstatus = (Integer) record.get("docstatus");
                    try {
                        // Handle documents based on docstatus
                        if (doctype.equals("Salary Slip") || doctype.equals("Salary Structure") || doctype.equals("Salary Structure Assignment")) {
                            if (docstatus == 0) {
                                // Draft document: delete directly
                                logger.info("{} {} is in Draft (docstatus: 0), deleting directly", doctype, recordName);
                            } else if (docstatus == 1) {
                                // Submitted document: cancel first
                                try {
                                    ResponseEntity<Map> cancelResponse = erpNextApiService.cancelResource(doctype, recordName, sid);
                                    if (cancelResponse.getStatusCode().is2xxSuccessful()) {
                                        results.add(String.format("%s %s successfully canceled", doctype, recordName));
                                        logger.info("{} {} successfully canceled", doctype, recordName);
                                    } else {
                                        String errorMsg = cancelResponse.getBody() != null ? cancelResponse.getBody().toString() : "Unknown error";
                                        results.add(String.format("Failed to cancel %s %s: %s", doctype, recordName, errorMsg));
                                        logger.error("Failed to cancel {} {}: {}", doctype, recordName, errorMsg);
                                        continue; // Skip deletion if cancellation fails
                                    }
                                } catch (HttpClientErrorException e) {
                                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                                    results.add(String.format("Error canceling %s %s: %s", doctype, recordName, errorMsg));
                                    logger.error("Error canceling {} {}: {}", doctype, recordName, errorMsg);
                                    continue;
                                }
                            } else if (docstatus == 2) {
                                // Already cancelled: proceed to deletion
                                logger.info("{} {} is already cancelled (docstatus: 2)", doctype, recordName);
                            }
                        }

                        // Delete the record
                        try {
                            ResponseEntity<Map> deleteResponse = erpNextApiService.deleteResource(doctype, recordName, sid);
                            if (deleteResponse.getStatusCode().is2xxSuccessful()) {
                                results.add(String.format("%s %s successfully deleted", doctype, recordName));
                                logger.info("{} {} successfully deleted", doctype, recordName);
                            } else {
                                String errorMsg = deleteResponse.getBody() != null ? deleteResponse.getBody().toString() : "Unknown error";
                                results.add(String.format("Failed to delete %s %s: %s", doctype, recordName, errorMsg));
                                logger.error("Failed to delete {} {}: {}", doctype, recordName, errorMsg);
                            }
                        } catch (HttpClientErrorException e) {
                            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                            results.add(String.format("Error deleting %s %s: %s", doctype, recordName, errorMsg));
                            logger.error("Error deleting {} {}: {}", doctype, recordName, errorMsg);
                        }
                    } catch (Exception e) {
                        results.add(String.format("Error processing %s %s: %s", doctype, recordName, e.getMessage()));
                        logger.error("Error processing {} {}: {}", doctype, recordName, e.getMessage(), e);
                    }
                }
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                results.add(String.format("Error fetching records for %s: %s", doctype, errorMsg));
                logger.error("Error fetching records for {}: {}", doctype, errorMsg);
            } catch (Exception e) {
                results.add(String.format("Error fetching records for %s: %s", doctype, e.getMessage()));
                logger.error("Error fetching records for {}: {}", doctype, e.getMessage());
            }
        }

        logger.info("Data reset completed: {} results", results.size());
        return results;
    }
}