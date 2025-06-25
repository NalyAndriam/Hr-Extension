package com.eval.erp.service;

import com.eval.erp.model.Component;
import com.eval.erp.model.Salary;
import com.eval.erp.model.SalaryComponent;
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

import javax.swing.RowFilter.ComparisonType;

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

    @Autowired
    private SalaryComponentService salaryComponentService;

    @Autowired
    private SalaryService salaryService;

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
                slip.setStatus((int) data.get("docstatus"));
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


    //-------------------------------------------------------------------------------------------------------------------------------------
    //----------------------------------------------------------------MODIF----------------------------------------------------------------
    //-------------------------------------------------------------------------------------------------------------------------------------

    // Filter SalarySlips by indemnity less than a specified amount
    public List<SalarySlip> filterSalarySlipsByComponent(String component, double maxComp, String signe, String sid) {
        List<Salary> filteredSlips = new ArrayList<>();

        try {
            // 1. Récupérer le composant de salaire à filtrer (ex: "Indemnité")
            SalaryComponent comp = salaryComponentService.getSalaryComponentByName(component, sid);

            // 2. Ne récupérer que les noms des Salary Slips pour alléger la requête initiale
            String fields = "[\"name\"]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, null, sid);
            List<Map<String, Object>> slipData = (List<Map<String, Object>>) response.getBody().get("data");
            List<Salary> salaries = salaryService.convertIntoSalaries(slipData);

            for (Salary sal : salaries) {
                // 3. Obtenir tous les détails du Salary Slip
                sal = salaryService.getPayslipById(sal.getName(), sid);

                // 🔎 Logs pour aider au débogage
                logger.info("Gross Pay for Salary Slip {}: {}", sal.getName(), sal.getGrossPay());

                StringBuilder earningsLog = new StringBuilder("Earnings: ");
                for (Component c : sal.getEarnings()) {
                    earningsLog.append(String.format("[%s: %.2f] ", c.getDescription(), c.getAmount()));
                }
                logger.info("Salary Slip {} - {}", sal.getName(), earningsLog.toString());

                StringBuilder deductionsLog = new StringBuilder("Deductions: ");
                for (Component c : sal.getDeductions()) {
                    deductionsLog.append(String.format("[%s: %.2f] ", c.getDescription(), c.getAmount()));
                }
                logger.info("Salary Slip {} - {}", sal.getName(), deductionsLog.toString());

                // 4. Filtrer selon le type (earning/deduction) et le montant
                List<Component> components = comp.getType().equals("Earning") ? sal.getEarnings() : sal.getDeductions();

                for (Component c : components) {
                    if (c.getDescription().equals(component)) {
                        if ("inferior".equals(signe) && c.getAmount() < maxComp) {
                            filteredSlips.add(sal);
                            break;
                        } else if (!"inferior".equals(signe) && c.getAmount() > maxComp) {
                            filteredSlips.add(sal);
                            break;
                        }
                    }
                }
            }

            logger.info("Found {} Salary Slips with component '{}' {} than {}", 
                        filteredSlips.size(), component, 
                        "inferior".equals(signe) ? "less" : "greater", 
                        maxComp);

            // ✅ Ne pas oublier de convertir la bonne liste !
            return salaryService.convertSalariesToSalarySlips(filteredSlips, sid);

        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            logger.error("API error filtering Salary Slips by component, instance: {}: {}", this.hashCode(), errorMsg);
            throw new RuntimeException("API error: " + errorMsg, e);
        } catch (Exception e) {
            logger.error("Unexpected error filtering Salary Slips, instance: {}: {}", this.hashCode(), e.getMessage());
            throw new RuntimeException("Error filtering Salary Slips: " + e.getMessage(), e);
        }
    }



    // Delete a SalarySlip by its ID, canceling it first if not already canceled
    public boolean deleteSalarySlip(String slipId, String sid) {
    logger.info("Attempting to delete Salary Slip with ID: {}, instance: {}", slipId, this.hashCode());
    try {
        // Step 1: Fetch SalarySlip details to identify associated SalaryStructureAssignment
        String slipFields = "[\"name\", \"status\", \"employee\", \"salary_structure\", \"start_date\", \"posting_date\"]";
        String slipFilters = String.format("[[\"name\",\"=\",\"%s\"]]", slipId);
        ResponseEntity<Map> slipResponse = erpNextApiService.getResource("Salary Slip", slipFields, slipFilters, sid);
        List<Map<String, Object>> slipData = (List<Map<String, Object>>) slipResponse.getBody().get("data");

        if (slipData == null || slipData.isEmpty()) {
            logger.warn("Salary Slip with ID {} does not exist, instance: {}", slipId, this.hashCode());
            throw new IllegalArgumentException("Salary Slip with ID " + slipId + " does not exist");
        }

        // Extract SalarySlip details
        Map<String, Object> slip = slipData.get(0);
        String status = (String) slip.get("status");
        String employeeId = (String) slip.get("employee");
        String salaryStructure = (String) slip.get("salary_structure");
        String payrollDate = (String) slip.get("start_date"); // Use start_date instead of payroll_date
        if (payrollDate == null) {
            payrollDate = (String) slip.get("posting_date"); // Fallback to posting_date if start_date is null
        }
        logger.debug("Salary Slip {} details: status={}, employee={}, salary_structure={}, payroll_date={}, instance: {}", 
                     slipId, status, employeeId, salaryStructure, payrollDate, this.hashCode());

        // Step 2: Find and delete associated SalaryStructureAssignment
        if (employeeId != null && salaryStructure != null && payrollDate != null) {
            boolean assignmentExists = checkSalaryStructureAssignmentExists(employeeId, salaryStructure, payrollDate, sid);
            if (assignmentExists) {
                // Fetch the SalaryStructureAssignment
                String assignmentFields = "[\"name\", \"docstatus\"]";
                String assignmentFilters = String.format(
                    "[[\"employee\",\"=\",\"%s\"],[\"salary_structure\",\"=\",\"%s\"],[\"from_date\",\"=\",\"%s\"]]",
                    employeeId, salaryStructure, payrollDate
                );
                ResponseEntity<Map> assignmentResponse = erpNextApiService.getResource(
                    "Salary Structure Assignment", assignmentFields, assignmentFilters, sid
                );
                List<Map<String, Object>> assignmentData = (List<Map<String, Object>>) assignmentResponse.getBody().get("data");

                if (assignmentData != null && !assignmentData.isEmpty()) {
                    String assignmentName = (String) assignmentData.get(0).get("name");
                    Integer docstatus = (Integer) assignmentData.get(0).get("docstatus"); // 0: Draft, 1: Submitted, 2: Cancelled
                    logger.debug("Found SalaryStructureAssignment {} for Salary Slip {}, docstatus={}, instance: {}", 
                                 assignmentName, slipId, docstatus, this.hashCode());

                    // Cancel the SalaryStructureAssignment if submitted
                    if (docstatus == 1) { // Submitted
                        logger.info("Canceling SalaryStructureAssignment {} for Salary Slip {}, instance: {}", 
                                    assignmentName, slipId, this.hashCode());
                        ResponseEntity<Map> cancelAssignmentResponse = erpNextApiService.cancelResource(
                            "Salary Structure Assignment", assignmentName, sid
                        );
                        if (!cancelAssignmentResponse.getStatusCode().is2xxSuccessful()) {
                            String errorMsg = cancelAssignmentResponse.getBody() != null 
                                             ? cancelAssignmentResponse.getBody().toString() 
                                             : "Unknown error";
                            logger.error("Failed to cancel SalaryStructureAssignment {}: {}, instance: {}", 
                                         assignmentName, errorMsg, this.hashCode());
                            throw new RuntimeException("Failed to cancel SalaryStructureAssignment: " + errorMsg);
                        }
                        logger.info("Successfully canceled SalaryStructureAssignment {}, instance: {}", 
                                    assignmentName, this.hashCode());
                    } else if (docstatus == 2) { // Already Cancelled
                        logger.info("SalaryStructureAssignment {} is already canceled, instance: {}", 
                                    assignmentName, this.hashCode());
                    } else {
                        logger.warn("SalaryStructureAssignment {} has unexpected docstatus: {}, instance: {}", 
                                    assignmentName, docstatus, this.hashCode());
                        // Optionally, decide whether to proceed or throw an exception
                    }

                    // Delete the SalaryStructureAssignment
                    logger.info("Deleting SalaryStructureAssignment {} for Salary Slip {}, instance: {}", 
                                assignmentName, slipId, this.hashCode());
                    ResponseEntity<Map> deleteAssignmentResponse = erpNextApiService.deleteResource(
                        "Salary Structure Assignment", assignmentName, sid
                    );
                    if (deleteAssignmentResponse.getStatusCode().is2xxSuccessful()) {
                        logger.info("Successfully deleted SalaryStructureAssignment {}, instance: {}", 
                                    assignmentName, this.hashCode());
                    } else {
                        String errorMsg = deleteAssignmentResponse.getBody() != null 
                                         ? deleteAssignmentResponse.getBody().toString() 
                                         : "Unknown error";
                        logger.error("Failed to delete SalaryStructureAssignment {}: {}, instance: {}", 
                                     assignmentName, errorMsg, this.hashCode());
                        throw new RuntimeException("Failed to delete SalaryStructureAssignment: " + errorMsg);
                    }
                }
            } else {
                logger.info("No SalaryStructureAssignment found for Salary Slip {}, employee {}, structure {}, date {}, instance: {}", 
                            slipId, employeeId, salaryStructure, payrollDate, this.hashCode());
            }
        } else {
            logger.warn("Incomplete Salary Slip data for finding SalaryStructureAssignment: employee={}, structure={}, payroll_date={}, instance: {}", 
                        employeeId, salaryStructure, payrollDate, this.hashCode());
        }

        // Step 3: Proceed with SalarySlip deletion
        // If the SalarySlip is submitted, cancel it first
        if ("Submitted".equalsIgnoreCase(status)) {
            logger.info("Canceling Salary Slip {} before deletion, instance: {}", slipId, this.hashCode());
            ResponseEntity<Map> cancelResponse = erpNextApiService.cancelResource("Salary Slip", slipId, sid);
            if (!cancelResponse.getStatusCode().is2xxSuccessful()) {
                String errorMsg = cancelResponse.getBody() != null ? cancelResponse.getBody().toString() : "Unknown error";
                logger.error("Failed to cancel Salary Slip {}: {}, instance: {}", slipId, errorMsg, this.hashCode());
                throw new RuntimeException("Failed to cancel Salary Slip: " + errorMsg);
            }
            logger.info("Successfully canceled Salary Slip {}, instance: {}", slipId, this.hashCode());
        } else if ("Cancelled".equalsIgnoreCase(status)) {
            logger.info("Salary Slip {} is already canceled, proceeding to delete, instance: {}", slipId, this.hashCode());
        } else {
            logger.warn("Salary Slip {} has unexpected status: {}, cannot proceed with deletion, instance: {}", 
                        slipId, status, this.hashCode());
            throw new IllegalStateException("Salary Slip " + slipId + " has unexpected status: " + status);
        }

        // Delete the SalarySlip
        ResponseEntity<Map> deleteResponse = erpNextApiService.deleteResource("Salary Slip", slipId, sid);
        if (deleteResponse.getStatusCode().is2xxSuccessful()) {
            logger.info("Successfully deleted Salary Slip {}, instance: {}", slipId, this.hashCode());
            return true;
        } else {
            String errorMsg = deleteResponse.getBody() != null ? deleteResponse.getBody().toString() : "Unknown error";
            logger.error("Failed to delete Salary Slip {}: {}, instance: {}", slipId, errorMsg, this.hashCode());
            throw new RuntimeException("Failed to delete Salary Slip: " + errorMsg);
        }

    } catch (HttpClientErrorException e) {
        String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
        logger.error("API error deleting Salary Slip {}, instance: {}: {}", slipId, this.hashCode(), errorMsg);
        throw new RuntimeException("API error deleting Salary Slip: " + errorMsg, e);
    } catch (Exception e) {
        logger.error("Error deleting Salary Slip {}, instance: {}: {}", slipId, this.hashCode(), e.getMessage());
        throw new RuntimeException("Error deleting Salary Slip: " + e.getMessage(), e);
    }
}


}