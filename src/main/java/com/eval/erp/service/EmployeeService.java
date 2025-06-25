package com.eval.erp.service;

import com.eval.erp.model.Employee;
import com.eval.erp.model.Salary;

import jakarta.servlet.http.HttpSession;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmployeeService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    private final ErpNextApiService erpNextApiService;
    private final UtilService utilService;
    private final CompanyService companyService;
    private final SalaryService salaryService;
    @Autowired
    private HttpSession session;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public EmployeeService(ErpNextApiService erpNextApiService, UtilService utilService, CompanyService companyService,
                           SalaryService salaryService) {
        this.erpNextApiService = erpNextApiService;
        this.utilService = utilService;
        this.companyService = companyService;
        this.salaryService= salaryService;
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

    private Employee convertSingleEmployee(Map<String, Object> data) {
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
        return dto;
    }

    public boolean createEmployee(Employee employee, String sid) {
        try {
            employee.setUtilService(utilService);
            employee.validate();
            Map<String, String> refToNameMap = (Map<String, String>) session.getAttribute("employeeRefToNameMap");
            if (refToNameMap == null) {
                refToNameMap = new HashMap<>();
                session.setAttribute("employeeRefToNameMap", refToNameMap);
            }
            String ref = employee.getName();
            if (checkEmployeeExists(ref, sid)) {
                logger.warn("Employee with ID {} already exists", ref);
                return false;
            }
            ResponseEntity<Map> response = erpNextApiService.postResource("Employee", employee.toMap(false), sid);
            if (response.getStatusCode().is2xxSuccessful()) {
                String erpName = (String) ((Map) response.getBody().get("data")).get("name");
                refToNameMap.put(ref, erpName);
                session.setAttribute("employeeRefToNameMap", refToNameMap);
                logger.info("Successfully created employee with ID: {}", ref);
                return true;
            }
            String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
            logger.error("Failed to create employee with ID {}: {}", ref, errorMsg);
            return false;
        } catch (Exception e) {
            logger.error("Error creating employee with ID {}: {}", employee.getName(), e.getMessage());
            throw new RuntimeException("Failed to create employee: " + e.getMessage(), e);
        }
    }

    public boolean checkEmployeeExists(String ref, String sid) throws Exception {
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

    public Employee getEmployeeById(String employeeId, String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            String filters = "[ [\"name\",\"=\",\"" + employeeId + "\"] ]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Employee", fields, filters, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from ERPNext API for employee {}: {}", employeeId, response);
                throw new Exception("Invalid response from ERPNext API");
            }
            List<Map<String, Object>> employeeData = (List<Map<String, Object>>) response.getBody().get("data");
            if (employeeData.isEmpty()) {
                throw new Exception("Employee not found: " + employeeId);
            }
            return convertSingleEmployee(employeeData.get(0));
        } catch (Exception e) {
            logger.error("Error fetching employee {}: {}", employeeId, e.getMessage(), e);
            throw new Exception("Error fetching employee: " + e.getMessage());
        }
    }

    public List<Salary> getEmployeeSalaries(String employeeId, String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            String filters = "[[\"employee\",\"=\",\"" + employeeId + "\"]]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, filters, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from ERPNext API for salaries of employee {}: {}", employeeId, response);
                throw new Exception("Invalid response from ERPNext API");
            }
            List<Map<String, Object>> salaryData = (List<Map<String, Object>>) response.getBody().get("data");
            return salaryService.convertIntoSalaries(salaryData);
        } catch (Exception e) {
            logger.error("Error fetching salaries for employee {}: {}", employeeId, e.getMessage(), e);
            throw new Exception("Error fetching salaries: " + e.getMessage());
        }
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

    public boolean updateEmployee(Employee employee, String sid) {
        try {
            employee.setUtilService(utilService);
            employee.validate();
            ResponseEntity<Map> response = erpNextApiService.updateResource("Employee", employee.getName(), employee.toMap(true), sid);
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully updated employee with ID {}", employee.getName());
                return true;
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                logger.error("Failed to update employee with ID {}: {}", employee.getName(), errorMsg);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error updating employee with ID {}: {}", employee.getName(), e.getMessage());
            throw new RuntimeException("Error updating employee: " + e.getMessage(), e);
        }
    }

    public boolean deleteEmployee(String employeeId, String sid) {
        try {
            ResponseEntity<Map> response = erpNextApiService.deleteResource("Employee", employeeId, sid);
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Successfully deleted employee with ID {}", employeeId);
                return true;
            } else {
                String errorMsg = response.getBody() != null ? response.getBody().toString() : "Unknown error";
                logger.error("Failed to delete employee with ID {}: {}", employeeId, errorMsg);
                return false;
            }
        } catch (HttpClientErrorException e) {
            logger.error("API error deleting employee with ID {}: {}", employeeId, e.getResponseBodyAsString());
            throw new RuntimeException("API error deleting employee: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Error deleting employee with ID {}: {}", employeeId, e.getMessage());
            throw new RuntimeException("Error deleting employee: " + e.getMessage(), e);
        }
    }

}