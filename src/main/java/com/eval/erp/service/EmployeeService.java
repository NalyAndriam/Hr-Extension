package com.eval.erp.service;

import com.eval.erp.model.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmployeeService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    private final ErpNextApiService erpNextApiService;
    private final UtilService utilService;
    private final CompanyService companyService;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public EmployeeService(ErpNextApiService erpNextApiService, UtilService utilService, CompanyService companyService) {
        this.erpNextApiService = erpNextApiService;
        this.utilService = utilService;
        this.companyService = companyService;
    }

    private List<Employee> convertIntoEmployee(List<Map<String, Object>> employeeData) {
        List<Employee> employees = new ArrayList<>();
        for (Map<String, Object> data : employeeData) {
            Employee dto = new Employee();
            dto.setName((String) data.get("name"));
            dto.setEmployeeName((String) data.get("employee_name"));
            dto.setDepartment((String) data.get("department"));
            dto.setDesignation((String) data.get("designation"));
            dto.setStatus((String) data.get("status"));
            String gender = (String) data.get("gender");
            if (gender != null) {
                dto.setGender(gender);
            }
            Date joining = utilService.getFormattedDate((String) data.get("date_of_joining"));
            Date birth = utilService.getFormattedDate((String) data.get("date_of_birth"));
            dto.setDateOfJoining(joining);
            dto.setDateOfBirth(birth);
            employees.add(dto);
        }
        return employees;
    }

    public List<Employee> getAllEmployees(String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Employee", fields, null, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from ERPNext API: {}", response);
                throw new Exception("Invalid response from ERPNext API");
            }
            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            return convertIntoEmployee(employeeData);
        } catch (Exception e) {
            logger.error("Error fetching employees: {}", e.getMessage(), e);
            throw new Exception("Error fetching employees: " + e.getMessage());
        }
    }

    public List<Employee> searchEmployees(Optional<String> name, Optional<String> id, Optional<String> department, 
                                         Optional<String> status, Optional<String> gender, 
                                         Optional<String> dateOfJoining, Optional<String> dateOfBirth, String sid) throws Exception {
        try {
            String fieldsJson = "[\"*\"]";
            List<String> filters = new ArrayList<>();
            if (name.isPresent() && !name.get().isEmpty()) {
                filters.add("[\"employee_name\",\"like\",\"%" + name.get() + "%\"]");
            }
            if (id.isPresent() && !id.get().isEmpty()) {
                filters.add("[\"name\",\"like\",\"%" + id.get() + "%\"]");
            }
            if (department.isPresent() && !department.get().isEmpty()) {
                filters.add("[\"department\",\"=\",\"" + department.get() + "\"]");
            }
            if (status.isPresent() && !status.get().isEmpty()) {
                filters.add("[\"status\",\"=\",\"" + status.get() + "\"]");
            }
            if (gender.isPresent() && !gender.get().isEmpty()) {
                filters.add("[\"gender\",\"=\",\"" + gender.get() + "\"]");
            }
            if (dateOfJoining.isPresent() && !dateOfJoining.get().isEmpty()) {
                filters.add("[\"date_of_joining\",\"=\",\"" + dateOfJoining.get() + "\"]");
            }
            if (dateOfBirth.isPresent() && !dateOfBirth.get().isEmpty()) {
                filters.add("[\"date_of_birth\",\"=\",\"" + dateOfBirth.get() + "\"]");
            }
            String filtersJson = filters.isEmpty() ? null : "[" + String.join(",", filters) + "]";
            logger.info("Calling getResource with fields: {}, filters: {}", fieldsJson, filtersJson);
            ResponseEntity<Map> response = erpNextApiService.getResource("Employee", fieldsJson, filtersJson, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from ERPNext API: {}", response);
                throw new Exception("Invalid response from ERPNext API");
            }
            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            return convertIntoEmployee(employeeData);
        } catch (Exception e) {
            logger.error("Error searching employees: {}", e.getMessage(), e);
            throw new Exception("Error searching employees: " + e.getMessage());
        }
    }

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
}