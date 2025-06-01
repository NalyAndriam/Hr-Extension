package com.eval.erp.service;

import com.eval.erp.model.Department;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DepartmentService {

    private static final Logger logger = LoggerFactory.getLogger(DepartmentService.class);

    private final ErpNextApiService erpNextApiService;

    public DepartmentService(ErpNextApiService erpNextApiService) {
        this.erpNextApiService = erpNextApiService;
    }

    private List<Department> convertIntoDepartment(List<Map<String, Object>> departmentData) {
        List<Department> departments = new ArrayList<>();
        for (Map<String, Object> data : departmentData) {
            Department dto = new Department();
            dto.setName((String) data.get("name"));
            departments.add(dto);
        }
        return departments;
    }

    public List<Department> getAllDepartments(String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Department", fields, null, sid);
            List<Map<String, Object>> departmentData = (List<Map<String, Object>>) response.getBody().get("data");
            return convertIntoDepartment(departmentData);
        } catch (Exception e) {
            logger.error("Error fetching departments: {}", e.getMessage());
            throw new Exception("Error fetching departments: " + e.getMessage());
        }
    }
}