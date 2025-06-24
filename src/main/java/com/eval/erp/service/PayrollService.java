package com.eval.erp.service;

import com.eval.erp.model.SalarySlip;
import com.eval.erp.model.SalaryStructure;
import com.eval.erp.model.SalaryStructureAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PayrollService {

    private static final Logger logger = LoggerFactory.getLogger(PayrollService.class);

    @Autowired
    private ErpNextApiService erpNextApiService;

    @Autowired
    @Lazy
    private UtilService utilService;

    @Autowired
    private ValidationService validationService;

    // Private constructor to prevent manual instantiation
    private PayrollService() {
        logger.info("PayrollService private constructor called, instance: {}", this.hashCode());
    }

    @PostConstruct
    public void init() {
        logger.info("PayrollService initialized, instance: {}, UtilService: {}, ValidationService: {}, ErpNextApiService: {}",
                this.hashCode(),
                utilService != null ? "injected" : "null",
                validationService != null ? "injected" : "null",
                erpNextApiService != null ? "injected" : "null");
    }

    // Insert a SalarySlip
    public int insertSalarySlip(SalarySlip salarySlip, String sid, HttpSession session) {
        logger.info("Entering insertSalarySlip, instance: {}, employeeId: {}, month: {}", 
                    this.hashCode(), salarySlip.getEmployeeId(), salarySlip.getMonth());
        try {
            if (utilService == null) {
                logger.error("UtilService is null in PayrollService, instance: {}", this.hashCode());
                throw new IllegalStateException("UtilService is not initialized");
            }

            // Set utilService in SalarySlip
            salarySlip.setUtilService(utilService);
            logger.debug("Set utilService in SalarySlip for employeeId: {}", salarySlip.getEmployeeId());

            // Validate SalarySlip data
            logger.debug("Validating SalarySlip for employee: {}", salarySlip.getEmployeeId());
            salarySlip.validate();

            // Map reference to ERPNext employee ID
            Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
            if (refToNameMap == null) {
                refToNameMap = new HashMap<>();
                session.setAttribute("employeeRefToNameMap", refToNameMap);
            }
            String employeeId = refToNameMap.getOrDefault(salarySlip.getEmployeeId(), salarySlip.getEmployeeId());

            // Check if employee exists
            if (!validationService.checkEmployeeExists(employeeId, sid, session)) {
                throw new IllegalArgumentException("Employee with ID " + employeeId + " does not exist");
            }

            // Check if salary structure exists
            if (!validationService.checkStructureExists(salarySlip.getSalaryStructure(), sid)) {
                throw new IllegalArgumentException("Salary Structure " + salarySlip.getSalaryStructure() + " does not exist");
            }

            // Check if Salary Component "Salaire Base" exists
            if (!validationService.checkComponentExists("Salaire Base", sid)) {
                throw new IllegalArgumentException("Salary Component 'Salaire Base' does not exist");
            }

            // Check and create Salary Structure Assignment if needed
            String payrollDate = utilService.formatDate(utilService.getFormattedDate(salarySlip.getMonth()), "yyyy-MM-dd");
            boolean assignmentCreated = false;
            if (!validationService.checkSalaryStructureAssignmentExists(employeeId, salarySlip.getSalaryStructure(), payrollDate, salarySlip.getBaseSalary(), sid)) {
                List<String> results = new ArrayList<>();
                assignmentCreated = validationService.createSalaryStructureAssignment(salarySlip, sid, 0, results, session);
                if (!assignmentCreated) {
                    throw new IllegalArgumentException("Failed to create Salary Structure Assignment: " + results.get(results.size() - 1));
                }
                logger.debug("Created new Salary Structure Assignment for employee: {}", employeeId);
            }

            // Check if Salary Slip exists
            boolean slipExists = validationService.checkSalarySlipExists(employeeId, salarySlip.getMonth(), sid);
            if (slipExists) {
                logger.info("Salary Slip already exists for employee {} and month {}, skipping creation, instance: {}", 
                            employeeId, salarySlip.getMonth(), this.hashCode());
                return 2; // Ignorer et retourner 2 pour passer au mois suivant
            }

            // Create new Salary Slip
            ResponseEntity<Map> response = erpNextApiService.postResource("Salary Slip", salarySlip.toMap(false), sid);
            String slipName = (String) ((Map) response.getBody().get("data")).get("name");
            logger.debug("Created new Salary Slip with ERPNext-assigned name: {}", slipName);

            if (response.getStatusCode().is2xxSuccessful()) {
                // Submit the Salary Slip
                ResponseEntity<Map> submitResponse = erpNextApiService.submitResource("Salary Slip", slipName, sid);
                if (submitResponse.getStatusCode().is2xxSuccessful()) {
                    // Update after submission to calculate total salary
                    Map<String, Object> updateData = new HashMap<>();
                    updateData.put("calculate_total_salary", 1);
                    erpNextApiService.updateResource("Salary Slip", slipName, updateData, sid);
                    logger.info("Successfully inserted Salary Slip {}, instance: {}", slipName, this.hashCode());
                    logger.debug("Salary Slip details: Employee: {}, Month: {}, Structure: {}, Base: {}", 
                                employeeId, salarySlip.getMonth(), salarySlip.getSalaryStructure(), salarySlip.getBaseSalary());
                    return 1;
                } else {
                    String errorMsg = submitResponse.getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                    throw new RuntimeException("Failed to submit Salary Slip: " + errorMsg);
                }
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                throw new RuntimeException("Failed to create Salary Slip: " + errorMsg);
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            logger.error("API error inserting Salary Slip, instance: {}: {}", this.hashCode(), errorMsg);
            throw new RuntimeException("API error inserting Salary Slip: " + errorMsg, e);
        } catch (Exception e) {
            logger.error("Error inserting Salary Slip, instance: {}: {}", this.hashCode(), e.getMessage());
            throw new RuntimeException("Error inserting Salary Slip: " + e.getMessage(), e);
        }
    }


    // Search SalarySlips with filters
    public List<SalarySlip> searchSalarySlips(Map<String, String> filters, String sid) {
        List<SalarySlip> salarySlips = new ArrayList<>();
        try {
            String fields = "[\"name\", \"employee\", \"start_date\", \"end_date\", \"gross_pay\", \"status\"]";
            String filterString = buildFilterString(filters);

            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, filterString, sid);
            List<Map<String, Object>> slipData = (List<Map<String, Object>>) response.getBody().get("data");

            for (Map<String, Object> data : slipData) {
                SalarySlip slip = new SalarySlip();
                slip.setUtilService(utilService); // Ensure utilService is set
                slip.setEmployeeId((String) data.get("employee"));
                slip.setMonth((String) data.get("start_date")); // Assuming start_date is formatted as yyyy-MM-dd
                slip.setBaseSalary(((Number) data.getOrDefault("gross_pay", 0)).doubleValue());
                slip.setSalaryStructure((String) data.getOrDefault("salary_structure", ""));
                salarySlips.add(slip);
            }

            logger.info("Found {} Salary Slips with filters: {}, instance: {}", salarySlips.size(), filterString, this.hashCode());
            return salarySlips;
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            logger.error("API error searching Salary Slips, instance: {}: {}", this.hashCode(), errorMsg);
            return salarySlips;
        } catch (Exception e) {
            logger.error("Error searching Salary Slips, instance: {}: {}", this.hashCode(), e.getMessage());
            throw new RuntimeException("Error searching Salary Slips: " + e.getMessage(), e);
        }
    }

    // Insert a SalaryStructureAssignment
    public String insertAssignment(SalaryStructureAssignment assignment, String sid, HttpSession session) {
        try {
            // Set utilService in assignment
            assignment.setUtilService(utilService);
            logger.debug("Set utilService in SalaryStructureAssignment for employeeId: {}", assignment.getEmployeeId());

            // Validate assignment data
            assignment.validate();

            // Check if employee exists
            if (!validationService.checkEmployeeExists(assignment.getEmployeeId(), sid, session)) {
                throw new IllegalArgumentException("Employee with ID " + assignment.getEmployeeId() + " does not exist");
            }

            // Check if salary structure exists
            if (!validationService.checkStructureExists(assignment.getSalaryStructure(), sid)) {
                throw new IllegalArgumentException("Salary Structure " + assignment.getSalaryStructure() + " does not exist");
            }

            // Check if company exists
            if (assignment.getCompany() == null) {
                String company = validationService.getEmployeeCompany(assignment.getEmployeeId(), sid);
                if (company == null) {
                    throw new IllegalArgumentException("Failed to fetch company for employee " + assignment.getEmployeeId());
                }
                assignment.setCompany(company);
            }

            // Check if assignment already exists
            if (validationService.checkSalaryStructureAssignmentExists(
                    assignment.getEmployeeId(),
                    assignment.getSalaryStructure(),
                    assignment.getFromDate(),
                    assignment.getBaseSalary(),
                    sid)) {
                return "Salary Structure Assignment already exists for employee " + assignment.getEmployeeId() + " and period " + assignment.getFromDate();
            }

            // Create the assignment
            ResponseEntity<Map> response = erpNextApiService.postResource("Salary Structure Assignment", assignment.toMap(false), sid);
            if (response.getStatusCode().is2xxSuccessful()) {
                String assignmentName = (String) ((Map) response.getBody().get("data")).get("name");

                // Submit the assignment
                ResponseEntity<Map> submitResponse = erpNextApiService.submitResource("Salary Structure Assignment", assignmentName, sid);
                if (submitResponse.getStatusCode().is2xxSuccessful()) {
                    logger.info("Successfully inserted Salary Structure Assignment {}, instance: {}", assignmentName, this.hashCode());
                    return "Salary Structure Assignment " + assignmentName + " created and submitted";
                } else {
                    String errorMsg = submitResponse.getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                    throw new RuntimeException("Failed to submit Salary Structure Assignment: " + errorMsg);
                }
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                throw new RuntimeException("Failed to create Salary Structure Assignment: " + errorMsg);
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            logger.error("API error inserting Salary Structure Assignment, instance: {}: {}", this.hashCode(), errorMsg);
            throw new RuntimeException("API error inserting Salary Structure Assignment: " + errorMsg, e);
        } catch (Exception e) {
            logger.error("Error inserting Salary Structure Assignment, instance: {}: {}", this.hashCode(), e.getMessage());
            throw new RuntimeException("Error inserting Salary Structure Assignment: " + e.getMessage(), e);
        }
    }

    // Search SalaryStructureAssignments with filters
    public List<SalaryStructureAssignment> searchSalaryStructureAssignments(String filterString, String sid) {
        try {
            List<SalaryStructureAssignment> assignments = new ArrayList<>();
            String fields = "[\"*\"]";

            // Use filterString directly
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Structure Assignment", fields, filterString, sid);
            List<Map<String, Object>> assignmentData = (List<Map<String, Object>>) response.getBody().get("data");

            for (Map<String, Object> data : assignmentData) {
                SalaryStructureAssignment assignment = new SalaryStructureAssignment();
                assignment.setUtilService(utilService); // Ensure utilService is set
                assignment.setEmployeeId((String) data.get("employee"));
                assignment.setSalaryStructure((String) data.get("salary_structure"));
                assignment.setFromDate((String) data.get("from_date"));
                assignment.setToDate((String) data.get("to_date"));
                assignment.setBaseSalary(((Number) data.getOrDefault("base", 0)).doubleValue());
                assignment.setCompany((String) data.get("company"));
                assignments.add(assignment);
            }

            logger.info("Found {} Salary Structure Assignments with filters: {}, instance: {}", assignments.size(), filterString, this.hashCode());
            return assignments;
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            logger.error("API error searching Salary Structure Assignments, instance: {}: {}", this.hashCode(), errorMsg);
            throw new RuntimeException("API error searching Salary Structure Assignments: " + errorMsg, e);
        } catch (Exception e) {
            logger.error("Error searching Salary Structure Assignments, instance: {}: {}", this.hashCode(), e.getMessage());
            throw new RuntimeException("Error searching Salary Structure Assignments: " + e.getMessage(), e);
        }
    }

    public boolean checkSalaryStructureAssignmentExists(String employeeId, String salaryStructure, String payrollDate, String sid) {
        try {
            String fields = "[\"name\", \"base\"]";
            String filters = String.format(
                "[[\"employee\",\"=\",\"%s\"],[\"salary_structure\",\"=\",\"%s\"],[\"from_date\",\"=\",\"%s\"]]",
                employeeId, salaryStructure, payrollDate
            );
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Structure Assignment", fields, filters, sid);
            List<Map<String, Object>> assignmentData = (List<Map<String, Object>>) response.getBody().get("data");
            logger.info("Checked Salary Structure Assignment for employee {}, structure {}, date {}, exists: {}, instance: {}", 
                        employeeId, salaryStructure, payrollDate, !assignmentData.isEmpty(), this.hashCode());
            return !assignmentData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking Salary Structure Assignment for employee {}, structure {}, date {}, instance: {}: {}", 
                        employeeId, salaryStructure, payrollDate, this.hashCode(), e.getMessage());
            return false;
        }
    }

    // Helper method to build filter string from map
    private String buildFilterString(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        List<String> filterList = new ArrayList<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            filterList.add(String.format("[\"%s\",\"=\",\"%s\"]", entry.getKey(), entry.getValue()));
        }
        return "[" + String.join(",", filterList) + "]";
    }

    public SalaryStructureAssignment getLastAssignmentBeforeMonth(String employeeId, String month, String sid) {
        try {
            String filterString = "["
                    + "[\"employee\",\"=\",\"" + employeeId + "\"],"
                    + "[\"from_date\",\"<=\",\"" + month + "\"]"
                    + "]&order_by=from_date desc&limit_page_length=1";

            List<SalaryStructureAssignment> assignments = searchSalaryStructureAssignments(filterString, sid);

            if (assignments.isEmpty()) {
                logger.info("No Salary Structure Assignment found for employee {} before {}, instance: {}", employeeId, month, this.hashCode());
                return null;
            }

            SalaryStructureAssignment latestAssignment = assignments.get(0);
            logger.info("Latest Salary Structure Assignment found for employee {} before {}: Employee: {}, Salary Structure: {}, From Date: {}, To Date: {}, Base Salary: {}, Company: {}, instance: {}",
                    employeeId, month,
                    latestAssignment.getEmployeeId(),
                    latestAssignment.getSalaryStructure(),
                    latestAssignment.getFromDate(),
                    latestAssignment.getToDate(),
                    latestAssignment.getBaseSalary(),
                    latestAssignment.getCompany(),
                    this.hashCode());

            return latestAssignment;

        } catch (Exception e) {
            logger.error("Error retrieving Salary Structure Assignment, instance: {}: {}", this.hashCode(), e.getMessage());
            return null;
        }
    }

    public SalaryStructure getSalaryStructure(String sid) {
        SalaryStructure structure = null;
        try {
            String fields = "[\"*\"]&limit_page_length=1";
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Structure", fields, null, sid);
            List<Map<String, Object>> structureData = (List<Map<String, Object>>) response.getBody().get("data");

            // Check if the list contains an element
            if (structureData != null && !structureData.isEmpty()) {
                Map<String, Object> data = structureData.get(0);
                structure = new SalaryStructure();
                structure.setSalaryStructureName((String) data.get("name"));
                structure.setCompany((String) data.get("company"));

                logger.info("Retrieved Salary Structure: {}, instance: {}", structure.getSalaryStructureName(), this.hashCode());
                return structure;
            } else {
                logger.warn("No Salary Structure found, instance: {}.", this.hashCode());
            }

        } catch (Exception e) {
            logger.error("Error retrieving Salary Structure, instance: {}: {}", this.hashCode(), e.getMessage());
        }
        return structure;
    }

    public boolean checkStructureExists(String structureName, String sid) throws Exception {
        boolean exists = validationService.checkStructureExists(structureName, sid);
        logger.info("Checked Salary Structure {} exists: {}, instance: {}", structureName, exists, this.hashCode());
        return exists;
    }
}