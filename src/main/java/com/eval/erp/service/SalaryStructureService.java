package com.eval.erp.service;

import com.eval.erp.model.SalaryComponent;
import com.eval.erp.model.SalaryStructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SalaryStructureService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    private final ErpNextApiService erpNextApiService;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public SalaryStructureService(ErpNextApiService erpNextApiService) {
        this.erpNextApiService = erpNextApiService;
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

    public boolean createSalaryStructure(SalaryStructure structure, String sid) {
        try {
            if (checkStructureExists(structure.getSalaryStructureName(), sid)) {
                logger.warn("Salary Structure {} already exists", structure.getSalaryStructureName());
                return false;
            }
            Map<String, Object> data = new HashMap<>();
            data.put("doctype", "Salary Structure");
            data.put("name", structure.getSalaryStructureName());
            data.put("company", structure.getCompany());
            data.put("payroll_frequency", "Monthly");
            data.put("is_active", "Yes");
            List<Map<String, Object>> earnings = new ArrayList<>();
            List<Map<String, Object>> deductions = new ArrayList<>();
            for (SalaryComponent component : structure.getComponents()) {
                component.validate();
                Map<String, Object> child = new HashMap<>();
                child.put("salary_component", component.getName());
                child.put("salary_component_abbr", component.getSalaryComponentAbbr());
                child.put("amount_based_on_formula", true);
                child.put("formula", component.getValeur().equalsIgnoreCase("base") ? "base" : component.getValeur());
                if (component.getType().equalsIgnoreCase("earning")) {
                    earnings.add(child);
                } else {
                    deductions.add(child);
                }
            }
            data.put("earnings", earnings);
            data.put("deductions", deductions);
            ResponseEntity<Map> response = erpNextApiService.postResource("Salary Structure", data, sid);
            if (response.getStatusCode().is2xxSuccessful()) {
                String structureName = (String) ((Map) response.getBody().get("data")).get("name");
                ResponseEntity<Map> submitResponse = erpNextApiService.submitResource("Salary Structure", structureName, sid);
                if (submitResponse.getStatusCode().is2xxSuccessful()) {
                    logger.info("Successfully created and submitted salary structure: {}", structureName);
                    return true;
                }
                String submitError = submitResponse.getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                logger.error("Failed to submit salary structure {}: {}", structureName, submitError);
                return false;
            }
            String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
            logger.error("Failed to create salary structure {}: {}", structure.getSalaryStructureName(), errorMsg);
            return false;
        } catch (Exception e) {
            logger.error("Error creating salary structure {}: {}", structure.getSalaryStructureName(), e.getMessage());
            throw new RuntimeException("Failed to create salary structure: " + e.getMessage(), e);
        }
    }

    private boolean checkStructureExists(String structureName, String sid) {
        try {
            String fields = "[\"name\"]";
            String filters = String.format("[[\"name\", \"=\", \"%s\"]]", structureName);
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Structure", fields, filters, sid);
            List<Map<String, Object>> structureData = (List<Map<String, Object>>) response.getBody().get("data");
            return !structureData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking structure existence for {}: {}", structureName, e.getMessage());
            return false;
        }
    }

    public boolean updateSalaryStructure(SalaryStructure structure, String sid) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("doctype", "Salary Structure");
            data.put("name", structure.getSalaryStructureName());
            data.put("company", structure.getCompany());
            data.put("payroll_frequency", "Monthly");
            data.put("is_active", "Yes");
            // Ajouter les earnings et deductions si nécessaires
            ResponseEntity<Map> response = erpNextApiService.updateResource("Salary Structure", structure.getSalaryStructureName(), data, sid);
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully updated salary structure {}", structure.getSalaryStructureName());
                return true;
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                logger.error("Failed to update salary structure {}: {}", structure.getSalaryStructureName(), errorMsg);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error updating salary structure {}: {}", structure.getSalaryStructureName(), e.getMessage());
            throw new RuntimeException("Error updating salary structure: " + e.getMessage(), e);
        }
    }

    public boolean deleteSalaryStructure(String structureName, String sid) {
        try {
            // Vérifier l'existence et le docstatus de la structure salariale
            String fields = "[\"name\", \"docstatus\"]";
            String filters = String.format("[[\"name\", \"=\", \"%s\"]]", structureName);
            ResponseEntity<Map> statusResponse = erpNextApiService.getResource("Salary Structure", fields, filters, sid);
            List<Map<String, Object>> structureData = (List<Map<String, Object>>) statusResponse.getBody().get("data");
            
            if (structureData.isEmpty()) {
                logger.warn("Salary Structure {} does not exist", structureName);
                return false;
            }

            Integer docstatus = (Integer) structureData.get(0).get("docstatus");

            // Annuler uniquement si docstatus == 1 (soumis)
            if (docstatus == 1) {
                ResponseEntity<Map> cancelResponse = erpNextApiService.cancelResource("Salary Structure", structureName, sid);
                if (!cancelResponse.getStatusCode().is2xxSuccessful()) {
                    String errorMsg = cancelResponse.getBody() != null ? cancelResponse.getBody().toString() : "Unknown error";
                    logger.error("Failed to cancel salary structure {}: {}", structureName, errorMsg);
                    return false;
                }
                logger.info("Successfully cancelled salary structure {}", structureName);
            } else if (docstatus == 0 || docstatus == 2) {
                logger.info("Salary Structure {} is in draft (docstatus=0) or cancelled (docstatus=2), no cancellation needed", structureName);
            } else {
                logger.error("Invalid docstatus {} for salary structure {}", docstatus, structureName);
                return false;
            }

            // Procéder à la suppression
            ResponseEntity<Map> deleteResponse = erpNextApiService.deleteResource("Salary Structure", structureName, sid);
            if (deleteResponse.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully deleted salary structure {}", structureName);
                return true;
            } else {
                String errorMsg = deleteResponse.getBody() != null ? deleteResponse.getBody().toString() : "Unknown error";
                logger.error("Failed to delete salary structure {}: {}", structureName, errorMsg);
                return false;
            }
        } catch (HttpClientErrorException e) {
            logger.error("API error deleting salary structure {}: {}", structureName, e.getResponseBodyAsString());
            throw new RuntimeException("API error deleting salary structure: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error deleting salary structure {}: {}", structureName, e.getMessage());
            throw new RuntimeException("Error deleting salary structure: " + e.getMessage(), e);
        }
    }


    


}