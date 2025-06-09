package com.eval.erp.service;

import com.eval.erp.model.Employee;
import com.eval.erp.model.SalaryComponent;
import com.eval.erp.model.SalarySlip;
import com.eval.erp.model.SalaryStructure;

import jakarta.servlet.http.HttpSession;

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

    @Autowired
    private HttpSession session;

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
        // Initialize or retrieve the ref-to-name mapping from the session
        Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
        if (refToNameMap == null) {
            refToNameMap = new HashMap<>();
            session.setAttribute("employeeRefToNameMap", refToNameMap);
        }
        
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
                    String ref = fields[0].trim(); // Store the Ref
                    employee.setName(ref); // Use Ref temporarily for validation
                    employee.setLastName(fields[1].trim());
                    employee.setFirstName(fields[2].trim());
                    employee.setGenre(fields[3].trim());
                    employee.setDateEmbauche(fields[4].trim());
                    employee.setDateNaissance(fields[5].trim());
                    employee.setCompany(fields[6].trim());

                    logger.info("Line {}: Raw employee data - Ref: {}, Nom: {}, Prenom: {}, genre: {}, Date embauche: {}, date naissance: {}, company: {}",
                            lineNumber, ref, employee.getLastName(), employee.getFirstName(),
                            employee.getGender(), employee.getDateEmbauche(), employee.getDateNaissance(), employee.getCompany());

                    employee.validate();
                    if (!companyService.ensureCompanyExists(employee.getCompany(), sid, lineNumber, results)) {
                        continue;
                    }
                    validateForApi(employee);

                    boolean employeeExists = checkEmployeeExists(ref, sid);
                    ResponseEntity<Map> response;
                    if (employeeExists) {
                        response = erpNextApiService.updateResource("Employee", refToNameMap.getOrDefault(ref, ref), employee.toMap(true), sid);
                    } else {
                        response = erpNextApiService.postResource("Employee", employee.toMap(false), sid);
                    }

                    if (response.getStatusCode().is2xxSuccessful()) {
                        String erpNextName = (String) ((Map) response.getBody().get("data")).get("name");
                        refToNameMap.put(ref, erpNextName); // Store the mapping
                        session.setAttribute("employeeRefToNameMap", refToNameMap); // Update session
                        results.add(String.format("Line %d: Employee %s (ERPNext ID: %s) successfully %s", lineNumber, ref, erpNextName, employeeExists ? "updated" : "created"));
                    } else {
                        String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                        results.add(String.format("Line %d: Failed to %s employee %s: %s", lineNumber, employeeExists ? "update" : "create", ref, errorMsg));
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

    private boolean checkEmployeeExists(String ref, String sid) throws Exception {
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

//-------------------------------------------------------SALARY STRUCTURE------------------------------------------------------------------

    public List<SalaryStructure> importSalaryStructure(MultipartFile file, String sid) throws Exception {
    logger.info("Processing CSV file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

    // Map pour regrouper les composants par structure salariale et société
    Map<String, List<SalaryComponent>> structureComponentsMap = new HashMap<>();
    Map<String, String> structureCompanyMap = new HashMap<>();
    List<SalaryStructure> summaries = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
        String headerLine = reader.readLine();
        if (headerLine == null || !headerLine.trim().toLowerCase().startsWith("salary structure,name,abbr,type,valeur,company")) {
            throw new IllegalArgumentException("Invalid CSV header. Expected: salary structure,name,abbr,type,valeur,company");
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
                component.setSalaryComponentAbbr(fields[2].trim());
                component.setType(fields[3].trim());
                component.setValeur(fields[4].trim());
                component.setCompany(fields[5].trim());

                logger.info("Line {}: Raw component data - Structure: {}, Name: {}, SalaryComponentAbbr: {}, Type: {}, Valeur: {}, Company: {}",
                        lineNumber, component.getSalaryStructure(), component.getName(), component.getSalaryComponentAbbr(),
                        component.getType(), component.getValeur(), component.getCompany());

                component.validate();

                // Ajouter le composant au groupe correspondant
                String key = component.getSalaryStructure() + "_" + component.getCompany();
                structureComponentsMap.computeIfAbsent(key, k -> new ArrayList<>()).add(component);
                structureCompanyMap.putIfAbsent(key, component.getCompany());

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

    if (structureComponentsMap.isEmpty()) {
        throw new Exception("Aucun composant valide trouvé dans le CSV.");
    }

    // Traiter chaque structure salariale
    for (Map.Entry<String, List<SalaryComponent>> entry : structureComponentsMap.entrySet()) {
        String[] keyParts = entry.getKey().split("_");
        String salaryStructureName = keyParts[0];
        String company = structureCompanyMap.get(entry.getKey());
        List<SalaryComponent> components = entry.getValue();
        SalaryStructure summary = new SalaryStructure(components, salaryStructureName, company);

        // Vérifier si la société existe
        if (!companyService.ensureCompanyExists(company, sid, 0, summary.getResults())) {
            summaries.add(summary);
            continue;
        }

        // Étape 1 : Créer ou mettre à jour les Salary Components
        for (SalaryComponent component : components) {
            int lineNumber = components.indexOf(component) + 2;
            try {
                boolean componentExists = checkComponentExists(utilService.normalizeName(component.getName()), sid);
                ResponseEntity<Map> response = null;
                logger.info("Tentative de {} du composant {} (name: {}, salary_component: {})", 
                            componentExists ? "mise à jour" : "création", 
                            component.getName(), utilService.normalizeName(component.getName()), component.getName());

                if (componentExists) {
                    summary.addResult(String.format("Line %d: Composant %s already exists, using existing component", 
                                                   lineNumber, component.getName()));
                    logger.info("Line {}: Composant {} already exists, using existing component", 
                                lineNumber, component.getName());
                } else {
                    response = erpNextApiService.postResource("Salary Component", component.toMap(false), sid);
                    if (response.getStatusCode().is2xxSuccessful()) {
                        summary.addResult(String.format("Line %d: Composant %s successfully created", 
                                                       lineNumber, component.getName()));
                        logger.info("Line {}: Composant {} successfully created", 
                                    lineNumber, component.getName());
                    } else {
                        String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                        summary.addResult(String.format("Line %d: Failed to create composant %s: %s", 
                                                       lineNumber, component.getName(), errorMsg));
                        logger.error("Line {}: Failed to create composant {}: {}", 
                                     lineNumber, component.getName(), errorMsg);
                    }
                }
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                if (errorMsg.contains("DuplicateEntryError")) {
                    summary.addResult(String.format("Line %d: Composant %s already exists, using existing component", 
                                                   lineNumber, component.getName()));
                    logger.info("Line {}: Composant {} already exists, using existing component", 
                                lineNumber, component.getName());
                } else {
                    summary.addResult(String.format("Line %d: API error for composant %s: %s", 
                                                   lineNumber, component.getName(), errorMsg));
                    logger.error("API error on line {} for composant {}: {}", 
                                 lineNumber, component.getName(), errorMsg);
                }
            } catch (Exception e) {
                summary.addResult(String.format("Line %d: Error processing composant %s: %s", 
                                               lineNumber, component.getName(), e.getMessage()));
                logger.error("Error processing line {} for composant {}: {}", 
                             lineNumber, component.getName(), e.getMessage());
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
                child.put("salary_component_abbr", component.getSalaryComponentAbbr());
                child.put("amount_based_on_formula", true);
                if (!component.getValeur().equalsIgnoreCase("base")) {
                    child.put("formula", component.getValeur());
                }
                if (component.getValeur().equalsIgnoreCase("base")) {
                    child.put("formula", "base");
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

        summaries.add(summary);
    }

    return summaries;
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


    //------------------------------------------------------------SALARY SLIP-----------------------------------------------------------

    public List<String> importSalarySlipsFromCsv(MultipartFile file, String sid) throws Exception {
        List<String> results = new ArrayList<>();
        Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
        if (refToNameMap == null) {
            refToNameMap = new HashMap<>();
            session.setAttribute("employeeRefToNameMap", refToNameMap);
        }

        logger.info("Processing Salary Slip CSV file: {}, size: {} bytes", file.getOriginalFilename(), file.getSize());

        // Vérifier si le composant "Basic Salary" existe
        String salaryComponent = "Salaire Base";
        if (!checkComponentExists(salaryComponent, sid)) {
            results.add("Error: Salary Component '" + salaryComponent + "' does not exist in ERPNext.");
            logger.error("Salary Component '{}' does not exist", salaryComponent);
            return results;
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || !headerLine.trim().toLowerCase().startsWith("mois,ref employe,salaire base,salaire")) {
                throw new IllegalArgumentException("Invalid CSV header. Expected: Mois,Ref Employe,Salaire Base,Salaire");
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

                    // Parser la ligne CSV en gérant les guillemets
                    String[] fields = parseCsvLine(line);
                    if (fields.length < 4) {
                        results.add(String.format("Line %d: Invalid number of fields, expected 4, found %d", lineNumber, fields.length));
                        logger.error("Line {}: Invalid number of fields, expected 4, found {}", lineNumber, fields.length);
                        continue;
                    }

                    // Créer un objet SalarySlip
                    SalarySlip salarySlip = new SalarySlip();
                    salarySlip.setUtilService(utilService);
                    salarySlip.setMonth(fields[0].trim());
                    String ref = fields[1].trim();
                    String employeeId = refToNameMap.getOrDefault(ref, ref); // Map Ref to ERPNext name
                    salarySlip.setEmployeeId(employeeId);
                    salarySlip.setBaseSalary(fields[2].trim()); // Le setter gère déjà les formats décimaux
                    salarySlip.setSalaryStructure(fields[3].trim());

                    logger.info("Line {}: Raw Salary Slip data - Month: {}, Employee Ref: {}, ERPNext ID: {}, Base Salary: {}, Salary Structure: {}",
                            lineNumber, salarySlip.getMonth(), ref, employeeId,
                            salarySlip.getBaseSalary(), salarySlip.getSalaryStructure());

                    // Valider les données
                    salarySlip.validate();

                    // Vérifier si l'employé existe
                    if (!checkEmployeeExists(ref, sid)) {
                        results.add(String.format("Line %d: Employee with Ref %s (ERPNext ID: %s) does not exist", lineNumber, ref, employeeId));
                        logger.error("Line {}: Employee with Ref {} (ERPNext ID: {}) does not exist", lineNumber, ref, employeeId);
                        continue;
                    }

                    // Vérifier si la structure salariale existe
                    if (!checkStructureExists(salarySlip.getSalaryStructure(), sid)) {
                        results.add(String.format("Line %d: Salary Structure %s does not exist", lineNumber, salarySlip.getSalaryStructure()));
                        logger.error("Line {}: Salary Structure {} does not exist", lineNumber, salarySlip.getSalaryStructure());
                        continue;
                    }

                    // Vérifier et créer une Salary Structure Assignment si nécessaire
                    String payrollDate = utilService.formatDate(utilService.getFormattedDate(salarySlip.getMonth()), "yyyy-MM-dd");
                    if (!checkSalaryStructureAssignmentExists(salarySlip.getEmployeeId(), salarySlip.getSalaryStructure(), payrollDate, salarySlip.getBaseSalary(), sid)) {
                        boolean assignmentCreated = createSalaryStructureAssignment(salarySlip, sid, lineNumber, results);
                        if (!assignmentCreated) {
                            results.add(String.format("Line %d: Failed to create Salary Structure Assignment for employee %s (Ref: %s)", lineNumber, employeeId, ref));
                            logger.error("Line {}: Failed to create Salary Structure Assignment for employee {} (Ref: {})", lineNumber, employeeId, ref);
                            continue;
                        }
                    }

                    // Créer ou mettre à jour le Salary Slip
                    boolean slipExists = checkSalarySlipExists(employeeId, salarySlip.getMonth(), sid);
                    ResponseEntity<Map> response;
                    if (slipExists) {
                        response = erpNextApiService.updateResource("Salary Slip", utilService.generateSalarySlipName(employeeId, salarySlip.getMonth()), salarySlip.toMap(true), sid);
                    } else {
                        response = erpNextApiService.postResource("Salary Slip", salarySlip.toMap(false), sid);
                    }

                    if (response.getStatusCode().is2xxSuccessful()) {
                        String slipName = slipExists ? utilService.generateSalarySlipName(employeeId, salarySlip.getMonth()) : (String) ((Map) response.getBody().get("data")).get("name");
                        results.add(String.format("Line %d: Salary Slip for employee %s (Ref: %s) successfully %s", lineNumber, employeeId, ref, slipExists ? "updated" : "created"));
                        logger.info("Line {}: Salary Slip for employee {} (Ref: {}) successfully {}", lineNumber, employeeId, ref, slipExists ? "updated" : "created");

                        // Soumettre le Salary Slip
                        ResponseEntity<Map> submitResponse = erpNextApiService.submitResource("Salary Slip", slipName, sid);
                        if (submitResponse.getStatusCode().is2xxSuccessful()) {
                            // Mettre à jour après soumission
                            Map<String, Object> updateData = new HashMap<>();
                            updateData.put("calculate_total_salary", 1);
                            ResponseEntity<Map> updateResponse = erpNextApiService.updateResource("Salary Slip", slipName, updateData, sid);
                        }
                    } else {
                        String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                        results.add(String.format("Line %d: Failed to %s Salary Slip for employee %s (Ref: %s): %s", lineNumber, slipExists ? "update" : "create", employeeId, ref, errorMsg));
                        logger.error("Line {}: Failed to {} Salary Slip for employee {} (Ref: {}): {}", lineNumber, slipExists ? "update" : "create", employeeId, ref, errorMsg);
                    }
                } catch (IllegalArgumentException e) {
                    results.add(String.format("Line %d: Validation error: %s", lineNumber, e.getMessage()));
                    logger.error("Validation error on line {}: {}", lineNumber, e.getMessage());
                } catch (HttpClientErrorException e) {
                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                    results.add(String.format("Line %d: API error: %s", lineNumber, errorMsg));
                    logger.error("API error on line {}: {}", lineNumber, errorMsg);
                } catch (Exception e) {
                    results.add(String.format("Line %d: Error processing Salary Slip: %s", lineNumber, e.getMessage()));
                    logger.error("Error processing line {}: {}", lineNumber, e.getMessage(), e);
                }
            }
        } catch (IllegalArgumentException e) {
            results.add("Error: " + e.getMessage());
            logger.error("CSV header error: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error importing Salary Slip CSV: {}", e.getMessage(), e);
            throw new Exception("Error importing Salary Slip CSV: " + e.getMessage());
        }

        return results;
    }

    // Méthode pour parser une ligne CSV en gérant les guillemets
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder field = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                fields.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        // Ajouter le dernier champ
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private boolean checkSalaryStructureAssignmentExists(String employeeId, String salaryStructure, String payrollDate, double baseSalary, String sid) {
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

    private boolean createSalaryStructureAssignment(SalarySlip salarySlip, String sid, int lineNumber, List<String> results) {
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

    private String getEmployeeCompany(String employeeId, String sid) {
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

    private boolean checkSalarySlipExists(String employeeId, String month, String sid) throws Exception {
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
}