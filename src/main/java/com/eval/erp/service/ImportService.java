package com.eval.erp.service;

import com.eval.erp.model.Employee;
import com.eval.erp.model.SalaryComponent;
import com.eval.erp.model.SalaryStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ImportService {

    private static final Logger logger = LoggerFactory.getLogger(ImportService.class);

    @Autowired
    private ErpNextApiService erpNextApiService;

    @Autowired
    private CompanyService companyService;
    
    @Autowired
    private UtilService utilService;

//-----------------------------------------------------------------------------EMPLOYEES--------------------------------------------

    public void validateForApi(Employee employee) throws IllegalArgumentException {
        if (employee.getFirstName() == null || employee.getFirstName().trim().isEmpty()) {
            throw new IllegalArgumentException("First name is required");
        }
        if (employee.getGender() == null || employee.getGender().trim().isEmpty()) {
            throw new IllegalArgumentException("Gender is required");
        }
        if (employee.getDateOfBirth() == null) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        if (employee.getDateOfJoining() == null) {
            throw new IllegalArgumentException("Date of joining is required");
        }
        if (employee.getCompany() == null || employee.getCompany().trim().isEmpty()) {
            throw new IllegalArgumentException("Company is required");
        }
    }

    public List<String> importEmployeesFromCsv(MultipartFile file, String sid) throws Exception {
        List<String> results = new ArrayList<>();
        logger.info("Processing CSV file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || !headerLine.trim().toLowerCase().startsWith("ref,nom,prenom,genre,date embauche,date naissance,company")) {
                throw new IllegalArgumentException("Invalid CSV header. Expected: Ref,Nom,Prenom,genre,Date embauche,date naissance,company");
            }
            logger.info("CSV header: {}", headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    if (line.trim().isEmpty()) {
                        results.add(String.format("Line %d: Skipped empty line", lineNumber));
                        logger.info("Line {}: Skipped empty line", lineNumber);
                        continue;
                    }

                    String[] fields = line.split(",");
                    if (fields.length < 7) {
                        results.add(String.format("Line %d: Invalid number of fields, expected 7, found %d", lineNumber, fields.length));
                        logger.error("Line {}: Invalid number of fields, expected 7, found {}", lineNumber, fields.length);
                        continue;
                    }

                    Employee employee = new Employee();
                    employee.setUtilService(utilService);
                    employee.setName(fields[0].trim());
                    employee.setLastName(fields[1].trim());
                    employee.setFirstName(fields[2].trim());
                    employee.setGenre(fields[3].trim());
                    employee.setDateEmbauche(fields[4].trim());
                    employee.setDateNaissance(fields[5].trim());
                    employee.setCompany(fields[6].trim());

                    logger.info("Line {}: Raw employee data - Ref: {}, Nom: {}, Prenom: {}, genre: {}, Date embauche: {}, date naissance: {}, company: {}",
                            lineNumber, employee.getName(), employee.getLastName(), employee.getFirstName(),
                            employee.getGender(), employee.getDateEmbauche(), employee.getDateNaissance(), employee.getCompany());

                    employee.validate();
                    if (!companyService.ensureCompanyExists(employee.getCompany(), sid, lineNumber, results)) {
                        continue;
                    }
                    validateForApi(employee);

                    boolean employeeExists = checkEmployeeExists(employee.getName(), sid);
                    ResponseEntity<Map> response;
                    if (employeeExists) {
                        response = erpNextApiService.updateResource("Employee", employee.getName(), employee.toMap(true), sid);
                    } else {
                        response = erpNextApiService.postResource("Employee", employee.toMap(false), sid);
                    }

                    if (response.getStatusCode().is2xxSuccessful()) {
                        results.add(String.format("Line %d: Employee %s successfully %s", lineNumber, employee.getName(), employeeExists ? "updated" : "created"));
                    } else {
                        String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                        results.add(String.format("Line %d: Failed to %s employee %s: %s", lineNumber, employeeExists ? "update" : "create", employee.getName(), errorMsg));
                    }
                } catch (IllegalArgumentException e) {
                    results.add(String.format("Line %d: Validation error: %s", lineNumber, e.getMessage()));
                    logger.error("Validation error on line {}: {}", lineNumber, e.getMessage());
                } catch (HttpClientErrorException e) {
                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                    results.add(String.format("Line %d: API error: %s", lineNumber, errorMsg));
                    logger.error("API error on line {}: {}", lineNumber, errorMsg);
                } catch (Exception e) {
                    results.add(String.format("Line %d: Error processing employee: %s", lineNumber, e.getMessage()));
                    logger.error("Error processing line {}: {}", lineNumber, e.getMessage(), e);
                }
            }
        } catch (IllegalArgumentException e) {
            results.add("Error: " + e.getMessage());
            logger.error("CSV header error: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error importing CSV: {}", e.getMessage(), e);
            throw new Exception("Error importing CSV: " + e.getMessage());
        }

        return results;
    }

    private boolean checkEmployeeExists(String employeeId, String sid) throws Exception {
        try {
            String fields = "[\"name\"]";
            String filters = "[ [\"name\",\"=\",\"" + employeeId + "\"] ]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Employee", fields, filters, sid);
            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            return !employeeData.isEmpty();
        } catch (Exception e) {
            logger.error("Error checking employee existence: {}", e.getMessage());
            return false;
        }
    }

//-------------------------------------------------------SALARY STRUCTURE------------------------------------------------------------------

    public SalaryStructure importSalaryStructure(MultipartFile file, String sid) throws Exception {
        logger.info("Processing CSV file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        List<SalaryComponent> components = new ArrayList<>();
        String salaryStructureName = null;
        String company = null;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || !headerLine.trim().toLowerCase().startsWith("salary structure,name,abbr,type,valeur,company")) {
                throw new IllegalArgumentException("Invalid CSV header. Expected: salary structure,name,Abbr,type,valeur,company");
            }
            logger.info("CSV header: {}", headerLine);

            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                try {
                    if (line.trim().isEmpty()) {
                        logger.info("Line {}: Skipped empty line", lineNumber);
                        continue;
                    }

                    String[] fields = line.split(",");
                    if (fields.length < 6) {
                        logger.error("Line {}: Invalid number of fields, expected 6, found {}", lineNumber, fields.length);
                        continue;
                    }

                    SalaryComponent component = new SalaryComponent();
                    component.setSalaryStructure(fields[0].trim());
                    component.setName(fields[1].trim());
                    component.setAbbr(fields[2].trim());
                    component.setType(fields[3].trim());
                    component.setValeur(fields[4].trim());
                    component.setCompany(fields[5].trim());

                    logger.info("Line {}: Raw component data - Structure: {}, Name: {}, Abbr: {}, Type: {}, Valeur: {}, Company: {}",
                            lineNumber, component.getSalaryStructure(), component.getName(), component.getAbbr(),
                            component.getType(), component.getValeur(), component.getCompany());

                    component.validate();
                    components.add(component);

                    if (salaryStructureName == null) {
                        salaryStructureName = component.getSalaryStructure();
                        company = component.getCompany();
                    } else if (!salaryStructureName.equals(component.getSalaryStructure()) || !company.equals(component.getCompany())) {
                        throw new IllegalArgumentException("Toutes les lignes doivent avoir la même structure salariale et la même société.");
                    }
                } catch (IllegalArgumentException e) {
                    logger.error("Validation error on line {}: {}", lineNumber, e.getMessage());
                } catch (Exception e) {
                    logger.error("Error processing line {}: {}", lineNumber, e.getMessage(), e);
                }
            }
        } catch (IllegalArgumentException e) {
            logger.error("CSV header error: {}", e.getMessage());
            throw new Exception("Erreur d'en-tête CSV : " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error reading CSV: {}", e.getMessage());
            throw new Exception("Erreur lors de la lecture du CSV : " + e.getMessage());
        }

        if (components.isEmpty()) {
            throw new Exception("Aucun composant valide trouvé dans le CSV.");
        }

        SalaryStructure summary = new SalaryStructure(components, salaryStructureName, company);

        // Vérifier si la société existe
        if (!companyService.ensureCompanyExists(company, sid, 0, summary.getResults())) {
            return summary;
        }

        // Étape 1 : Créer ou mettre à jour les Salary Components
        for (SalaryComponent component : components) {
            int lineNumber = components.indexOf(component) + 2;
            try {
                boolean componentExists = checkComponentExists(utilService.normalizeName(component.getName()), sid); // Vérifier avec le nom original
                ResponseEntity<Map> response;
                logger.info("Tentative de {} du composant {} (name: {}, salary_component: {})", componentExists ? "mise à jour" : "création", component.getName(), utilService.normalizeName(component.getName()), component.getName());
                if (componentExists) {
                    response = erpNextApiService.updateResource("Salary Component", utilService.normalizeName(component.getName()), component.toMap(true), sid);
                } else {
                    response = erpNextApiService.postResource("Salary Component", component.toMap(false), sid);
                }

                if (response.getStatusCode().is2xxSuccessful()) {
                    summary.addResult(String.format("Line %d: Composant %s successfully %s", lineNumber, component.getName(), componentExists ? "updated" : "created"));
                    logger.info("Line {}: Composant {} successfully {}", lineNumber, component.getName(), componentExists ? "updated" : "created");
                } else {
                    String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                    summary.addResult(String.format("Line %d: Failed to %s composant %s: %s", lineNumber, componentExists ? "update" : "create", component.getName(), errorMsg));
                    logger.error("Line {}: Failed to {} composant {}: {}", lineNumber, componentExists ? "update" : "create", component.getName(), errorMsg);
                }
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                summary.addResult(String.format("Line %d: API error for composant %s: %s", lineNumber, component.getName(), errorMsg));
                logger.error("API error on line {} for composant {}: {}", lineNumber, component.getName(), errorMsg);
            } catch (Exception e) {
                summary.addResult(String.format("Line %d: Error processing composant %s: %s", lineNumber, component.getName(), e.getMessage()));
                logger.error("Error processing line {} for composant {}: {}", lineNumber, component.getName(), e.getMessage());
            }
        }

        // Étape 2 : Créer ou mettre à jour la Salary Structure
        try {
            boolean structureExists = checkStructureExists(salaryStructureName, sid);
            ResponseEntity<Map> response;
            Map<String, Object> data = new HashMap<>();
            data.put("doctype", "Salary Structure");
            data.put("name", salaryStructureName);
            data.put("company", company);
            data.put("payroll_frequency", "Monthly");
            data.put("is_active", "Yes");

            List<Map<String, Object>> earnings = new ArrayList<>();
            List<Map<String, Object>> deductions = new ArrayList<>();

            for (SalaryComponent component : components) {
                Map<String, Object> child = new HashMap<>();
                child.put("salary_component", component.getName());
                child.put("abbr", component.getAbbr());
                if (!component.getValeur().equalsIgnoreCase("base")) {
                    child.put("formula", component.getValeur());
                }
                if (component.getType().equalsIgnoreCase("earning")) {
                    earnings.add(child);
                } else {
                    deductions.add(child);
                }
            }

            data.put("earnings", earnings);
            data.put("deductions", deductions);
            logger.info("Données envoyées pour la structure salariale '{}': {}", salaryStructureName, data);

            if (structureExists) {
                response = erpNextApiService.updateResource("Salary Structure", salaryStructureName, data, sid);
            } else {
                response = erpNextApiService.postResource("Salary Structure", data, sid);
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                summary.addResult(String.format("Structure %s successfully %s", salaryStructureName, structureExists ? "updated" : "created"));
                logger.info("Structure {} successfully {}", salaryStructureName, structureExists ? "updated" : "created");

                // Soumettre la structure salariale
                try {
                    ResponseEntity<Map> submitResponse = erpNextApiService.submitResource("Salary Structure", salaryStructureName, sid);
                    if (submitResponse.getStatusCode().is2xxSuccessful()) {
                        summary.addResult(String.format("Structure %s successfully submitted", salaryStructureName));
                        logger.info("Structure {} successfully submitted", salaryStructureName);
                    } else {
                        String errorMsg = submitResponse.getBody() != null ? submitResponse.getBody().toString() : "Unknown error";
                        summary.addResult(String.format("Failed to submit structure %s: %s", salaryStructureName, errorMsg));
                        logger.error("Failed to submit structure {}: {}", salaryStructureName, errorMsg);
                    }
                } catch (HttpClientErrorException e) {
                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                    summary.addResult(String.format("API error submitting structure %s: %s", salaryStructureName, errorMsg));
                    logger.error("API error submitting structure {}: {}", salaryStructureName, errorMsg);
                } catch (Exception e) {
                    summary.addResult(String.format("Error submitting structure %s: %s", salaryStructureName, e.getMessage()));
                    logger.error("Error submitting structure {}: {}", salaryStructureName, e.getMessage());
                }
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                summary.addResult(String.format("Failed to %s structure %s: %s", structureExists ? "update" : "create", salaryStructureName, errorMsg));
                logger.error("Failed to {} structure {}: {}", structureExists ? "update" : "create", salaryStructureName, errorMsg);
            }
        } catch (Exception e) {
            summary.addResult(String.format("Error processing structure %s: %s", salaryStructureName, e.getMessage()));
            logger.error("Error processing structure {}: {}", salaryStructureName, e.getMessage());
        }

        return summary;
    }

    

    private boolean checkComponentExists(String componentName, String sid) throws Exception {
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

    private boolean checkStructureExists(String structureName, String sid) throws Exception {
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
}