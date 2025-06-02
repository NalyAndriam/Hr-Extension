package com.eval.erp.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

@Service
public class CompanyService {

    private final ErpNextApiService erpNextApiService;
    private static final Logger logger = LoggerFactory.getLogger(CompanyService.class);

    public CompanyService(ErpNextApiService erpNextApiService) {
        this.erpNextApiService = erpNextApiService;
    }

    public boolean ensureCompanyExists(String companyName, String sid, int lineNumber, List<String> results) {
        try {
            // Check if company exists
            String fields = "[\"name\"]";
            String filters = "[ [\"name\",\"=\",\"" + companyName + "\"] ]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Company", fields, filters, sid);
            List<Map<String, Object>> companyData = (List<Map<String, Object>>) response.getBody().get("data");

            if (!companyData.isEmpty()) {
                logger.info("Company '{}' exists for line {}", companyName, lineNumber);
                return true;
            }

            // Company doesn't exist, create it
            logger.info("Company '{}' does not exist, creating for line {}", companyName, lineNumber);
            Map<String, Object> companyPayload = new HashMap<>();
            companyPayload.put("company_name", companyName);
            // Generate abbreviation (e.g., first 3 letters of company name)
            String abbr = companyName.length() >= 3 ? companyName.substring(0, 3).toUpperCase() : companyName.toUpperCase();
            companyPayload.put("abbr", abbr);
            // Add mandatory fields
            companyPayload.put("default_currency", "USD"); // Default currency, adjust as needed
            companyPayload.put("country", "United States"); // Default country, adjust as needed

            ResponseEntity<Map> createResponse = erpNextApiService.postResource("Company", companyPayload, sid);
            if (createResponse.getStatusCode().is2xxSuccessful()) {
                results.add(String.format("Line %d: Company '%s' successfully created", lineNumber, companyName));
                logger.info("Company '{}' created successfully for line {}", companyName, lineNumber);
                return true;
            } else {
                String errorMsg = createResponse.getBody() != null ? createResponse.getBody().toString() : "Unknown error";
                results.add(String.format("Line %d: Failed to create company '%s': %s", lineNumber, companyName, errorMsg));
                logger.error("Failed to create company '{}' for line {}: {}", companyName, lineNumber, errorMsg);
                return false;
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            results.add(String.format("Line %d: Error checking/creating company '%s': %s", lineNumber, companyName, errorMsg));
            logger.error("Error checking/creating company '{}' for line {}: {}", companyName, lineNumber, errorMsg);
            return false;
        } catch (Exception e) {
            results.add(String.format("Line %d: Error checking/creating company '%s': %s", lineNumber, companyName, e.getMessage()));
            logger.error("Error checking/creating company '{}' for line {}: {}", companyName, lineNumber, e.getMessage(), e);
            return false;
        }
    }
}