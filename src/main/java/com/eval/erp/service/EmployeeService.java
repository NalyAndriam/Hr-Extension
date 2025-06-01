package com.eval.erp.service;

import com.eval.erp.model.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class EmployeeService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    private final ErpNextApiService erpNextApiService;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public EmployeeService(ErpNextApiService erpNextApiService) {
        this.erpNextApiService = erpNextApiService;
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

    public List<Employee> searchEmployees(Optional<String> name, Optional<String> department, Optional<String> status, String sid) throws Exception {
        try {
            String fieldsJson = "[\"*\"]";

            // Build filters as a JSON array
            List<String> filters = new ArrayList<>();
            if (name.isPresent() && !name.get().isEmpty()) {
                filters.add("[\"employee_name\",\"like\",\"%" + name.get() + "%\"]");
            }
            if (department.isPresent() && !department.get().isEmpty()) {
                filters.add("[\"department\",\"=\",\"" + department.get() + "\"]");
            }
            if (status.isPresent() && !status.get().isEmpty()) {
                filters.add("[\"status\",\"=\",\"" + status.get() + "\"]");
            }

            // Combine filters into a JSON string
            String filtersJson = filters.isEmpty() ? null : "[" + String.join(",", filters) + "]";

            // Log the parameters for debugging
            logger.info("Calling getResource with fields: {}, filters: {}", fieldsJson, filtersJson);

            // Make the API call
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
}