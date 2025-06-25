package com.eval.erp.service;

import com.eval.erp.model.SalarySlip;
import com.eval.erp.model.SalaryStructureAssignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ValidationService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationService.class);

    @Autowired
    private ErpNextApiService erpNextApiService;

    @Autowired
    private UtilService utilService;

    public boolean checkEmployeeExists(String ref, String sid, HttpSession session) throws Exception {
        Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
        String employeeId = refToNameMap != null ? refToNameMap.getOrDefault(ref, ref) : ref;
        try {
            String fields = "[\"name\"]";
            String filters = "[ [\"name\",\"=\",\"" + employeeId + "\"] ]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Employee", fields, filters, sid);
            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            return !employeeData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking employee existence for ref {} (ERPNext ID: {}): {}", ref, employeeId, e.getMessage());
            return false;
        }
    }

    public boolean checkComponentExists(String componentName, String sid) throws Exception {
        try {
            String fields = "[\"name\"]";
            String filters = "[[\"salary_component\",\"=\",\"" + componentName + "\"]]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Component", fields, filters, sid);
            List<Map<String, Object>> componentData = (List<Map<String, Object>>) response.getBody().get("data");
            logger.info("Vérification de l'existence du composant avec salary_component: {}, trouvé: {}", componentName, !componentData.isEmpty());
            return !componentData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking component existence for {}: {}", componentName, e.getMessage());
            throw new Exception("Erreur lors de la vérification de l'existence du composant: " + e.getMessage());
        }
    }

    public boolean checkStructureExists(String structureName, String sid) throws Exception {
        try {
            String fields = "[\"name\"]";
            String filters = "[[\"name\",\"=\",\"" + structureName + "\"]]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Structure", fields, filters, sid);
            List<Map<String, Object>> structureData = (List<Map<String, Object>>) response.getBody().get("data");
            return !structureData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking structure existence: {}", e.getMessage());
            return false;
        }
    }

    public boolean checkSalarySlipExists(String employeeId, String month, String sid) throws Exception {
        try {
            String fields = "[\"name\"]";
            String formattedMonth = utilService.formatDate(utilService.getFormattedDate(month), "yyyy-MM-dd");
            String filters = String.format("[[\"employee\",\"=\",\"%s\"],[\"start_date\",\"=\",\"%s\"]]", employeeId, formattedMonth);
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, filters, sid);
            List<Map<String, Object>> slipData = (List<Map<String, Object>>) response.getBody().get("data");
            return !slipData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking Salary Slip existence for employee {} and month {}: {}", employeeId, month, e.getMessage());
            return false;
        }
    }

    public boolean createSalaryStructureAssignment(SalarySlip salarySlip, String sid, int lineNumber, List<String> results, HttpSession session) {
        try {
            Map<String, Object> assignmentPayload = new HashMap<>();
            assignmentPayload.put("doctype", "Salary Structure Assignment");
            assignmentPayload.put("employee", salarySlip.getEmployeeId());
            assignmentPayload.put("salary_structure", salarySlip.getSalaryStructure());

            // Calculer la date de début de la période du bulletin (premier jour du mois)
            String payrollDate = utilService.formatDate(utilService.getFormattedDate(salarySlip.getMonth()), "yyyy-MM-dd");
            assignmentPayload.put("from_date", payrollDate);

            // Calculer la date de fin (dernier jour du mois)
            String toDate = utilService.getEndOfMonth(salarySlip.getMonth(), "yyyy-MM-dd");
            assignmentPayload.put("to_date", toDate);

            // Définir le salaire de base
            assignmentPayload.put("base", salarySlip.getBaseSalary());

            // Récupérer la compagnie
            String company = getEmployeeCompany(salarySlip.getEmployeeId(), sid);
            if (company == null) {
                results.add(String.format("Line %d: Failed to fetch company for employee %s", lineNumber, salarySlip.getEmployeeId()));
                logger.error("Line {}: Failed to fetch company for employee {}", lineNumber, salarySlip.getEmployeeId());
                return false;
            }
            assignmentPayload.put("company", company);

            logger.info("Line {}: Creating Salary Structure Assignment for employee {} with payload: {}", 
                        lineNumber, salarySlip.getEmployeeId(), assignmentPayload);

            // Vérifier si une affectation existe déjà pour la période exacte et le salaire de base
            if (checkSalaryStructureAssignmentExists(salarySlip.getEmployeeId(), salarySlip.getSalaryStructure(), payrollDate, salarySlip.getBaseSalary(), sid)) {
                results.add(String.format("Line %d: Salary Structure Assignment already exists for employee %s, period %s", 
                                        lineNumber, salarySlip.getEmployeeId(), payrollDate));
                logger.info("Line {}: Salary Structure Assignment already exists for employee {}, period {}", 
                            lineNumber, salarySlip.getEmployeeId(), payrollDate);
                return true; // L'affectation existe déjà, pas besoin de la recréer
            }

            // Créer l'affectation
            ResponseEntity<Map> response = erpNextApiService.postResource("Salary Structure Assignment", assignmentPayload, sid);
            if (response.getStatusCode().is2xxSuccessful()) {
                String assignmentName = (String) ((Map) response.getBody().get("data")).get("name");
                results.add(String.format("Line %d: Salary Structure Assignment %s created for employee %s", 
                                        lineNumber, assignmentName, salarySlip.getEmployeeId()));
                logger.info("Line {}: Salary Structure Assignment {} created for employee {}", 
                            lineNumber, assignmentName, salarySlip.getEmployeeId());

                // Soumettre l'affectation
                ResponseEntity<Map> submitResponse = erpNextApiService.submitResource("Salary Structure Assignment", assignmentName, sid);
                if (submitResponse.getStatusCode().is2xxSuccessful()) {
                    results.add(String.format("Line %d: Salary Structure Assignment %s submitted for employee %s", 
                                            lineNumber, assignmentName, salarySlip.getEmployeeId()));
                    logger.info("Line {}: Salary Structure Assignment {} submitted for employee {}", 
                                lineNumber, assignmentName, salarySlip.getEmployeeId());
                    return true;
                } else {
                    String errorMsg = submitResponse.getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                    results.add(String.format("Line %d: Failed to submit Salary Structure Assignment %s for employee %s: %s", 
                                            lineNumber, assignmentName, salarySlip.getEmployeeId(), errorMsg));
                    logger.error("Line {}: Failed to submit Salary Structure Assignment {} for employee {}: {}", 
                                lineNumber, assignmentName, salarySlip.getEmployeeId(), errorMsg);
                    return false;
                }
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                results.add(String.format("Line %d: Failed to create Salary Structure Assignment for employee %s: %s", 
                                        lineNumber, salarySlip.getEmployeeId(), errorMsg));
                logger.error("Line {}: Failed to create Salary Structure Assignment for employee {}: {}", 
                            lineNumber, salarySlip.getEmployeeId(), errorMsg);
                return false;
            }
        } catch (HttpClientErrorException e) {
            String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
            results.add(String.format("Line %d: Error creating/submitting Salary Structure Assignment for employee %s: %s", 
                                    lineNumber, salarySlip.getEmployeeId(), errorMsg));
            logger.error("Line {}: Error creating/submitting Salary Structure Assignment for employee {}: {}", 
                        lineNumber, salarySlip.getEmployeeId(), errorMsg, e);
            return false;
        } catch (Exception e) {
            results.add(String.format("Line %d: Error creating/submitting Salary Structure Assignment for employee %s: %s", 
                                    lineNumber, salarySlip.getEmployeeId(), e.getMessage()));
            logger.error("Line {}: Error creating/submitting Salary Structure Assignment for employee {}: {}", 
                        lineNumber, salarySlip.getEmployeeId(), e.getMessage(), e);
            return false;
        }
    }

    public String getEmployeeCompany(String employeeId, String sid) { 
        try {
            String fields = "[\"company\"]";
            String filters = String.format("[[\"name\",\"=\",\"%s\"]]", employeeId);
            ResponseEntity<Map> response = erpNextApiService.getResource("Employee", fields, filters, sid);
            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            if (!employeeData.isEmpty()) {
                return (String) employeeData.get(0).get("company");
            }
            return null;
        } catch (Exception e) {
            logger.error("Error fetching company for employee {}: {}", employeeId, e.getMessage());
            return null;
        }
    }

    public boolean checkSalaryStructureAssignmentExists(String employeeId, String salaryStructure, String payrollDate, double baseSalary, String sid) {
        try {
            String fields = "[\"name\", \"base\"]";
            String filters = String.format(
                "[[\"employee\",\"=\",\"%s\"],[\"salary_structure\",\"=\",\"%s\"],[\"from_date\",\"=\",\"%s\"],[\"base\",\"=\",\"%s\"]]",
                employeeId, salaryStructure, payrollDate, baseSalary
            );
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Structure Assignment", fields, filters, sid);
            List<Map<String, Object>> assignmentData = (List<Map<String, Object>>) response.getBody().get("data");
            return !assignmentData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking Salary Structure Assignment for employee {}, structure {}, date {}, base {}: {}", 
                        employeeId, salaryStructure, payrollDate, baseSalary, e.getMessage());
            return false;
        }
    }

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
}