package com.eval.erp.service;

import java.util.ArrayList;
import java.util.Calendar;
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

            // Create a default Holiday List for the company
            String holidayListName = createDefaultHolidayList(companyName, sid, lineNumber, results);
            if (holidayListName != null) {
                companyPayload.put("default_holiday_list", holidayListName);
            } else {
                results.add(String.format("Line %d: Failed to create default Holiday List for company '%s'", lineNumber, companyName));
                logger.error("Failed to create default Holiday List for company '{}' for line {}", companyName, lineNumber);
                return false;
            }

            ResponseEntity<Map> createResponse = erpNextApiService.postResource("Company", companyPayload, sid);
            if (createResponse.getStatusCode().is2xxSuccessful()) {
                results.add(String.format("Line %d: Company '%s' successfully created with Holiday List '%s'", lineNumber, companyName, holidayListName));
                logger.info("Company '{}' created successfully with Holiday List '{}' for line {}", companyName, holidayListName, lineNumber);
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

    private String createDefaultHolidayList(String companyName, String sid, int lineNumber, List<String> results) {
        try {
            // Generate a unique Holiday List name
            int currentYear = Calendar.getInstance().get(Calendar.YEAR);
            String holidayListName = String.format("%s Holiday List %d", companyName, currentYear);

            // Check if Holiday List already exists
            String fields = "[\"name\"]";
            String filters = "[ [\"name\",\"=\",\"" + holidayListName + "\"] ]";
            ResponseEntity<Map> checkResponse = erpNextApiService.getResource("Holiday List", fields, filters, sid);
            List<Map<String, Object>> holidayListData = (List<Map<String, Object>>) checkResponse.getBody().get("data");

            if (!holidayListData.isEmpty()) {
                logger.info("Holiday List '{}' already exists for company '{}'", holidayListName, companyName);
                return holidayListName;
            }

            // Create a new Holiday List
            Map<String, Object> holidayListPayload = new HashMap<>();
            holidayListPayload.put("holiday_list_name", holidayListName);
            holidayListPayload.put("from_date", String.format("%d-01-01", currentYear));
            holidayListPayload.put("to_date", String.format("%d-12-31", currentYear));
            holidayListPayload.put("weekly_off", "Sunday"); // Set Sunday as the default weekly off

            // Add a sample holiday (optional, adjust as needed)
            List<Map<String, Object>> holidays = new ArrayList<>();
            Map<String, Object> sampleHoliday = new HashMap<>();
            sampleHoliday.put("description", "New Year's Day");
            sampleHoliday.put("holiday_date", String.format("%d-01-01", currentYear));
            holidays.add(sampleHoliday);
            holidayListPayload.put("holidays", holidays);

            ResponseEntity<Map> createResponse = erpNextApiService.postResource("Holiday List", holidayListPayload, sid);
            if (createResponse.getStatusCode().is2xxSuccessful()) {
                results.add(String.format("Line %d: Holiday List '%s' successfully created for company '%s'", lineNumber, holidayListName, companyName));
                logger.info("Holiday List '{}' created successfully for company '{}'", holidayListName, companyName);
                return holidayListName;
            } else {
                String errorMsg = createResponse.getBody() != null ? createResponse.getBody().toString() : "Unknown error";
                results.add(String.format("Line %d: Failed to create Holiday List '%s': %s", lineNumber, holidayListName, errorMsg));
                logger.error("Failed to create Holiday List '{}' for company '{}': {}", holidayListName, companyName, errorMsg);
                return null;
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            results.add(String.format("Line %d: Error creating Holiday List for company '%s': %s", lineNumber, companyName, errorMsg));
            logger.error("Error creating Holiday List for company '{}': {}", companyName, errorMsg);
            return null;
        } catch (Exception e) {
            results.add(String.format("Line %d: Error creating Holiday List for company '%s': %s", lineNumber, companyName, e.getMessage()));
            logger.error("Error creating Holiday List for company '{}': {}", companyName, e.getMessage(), e);
            return null;
        }
    }
}