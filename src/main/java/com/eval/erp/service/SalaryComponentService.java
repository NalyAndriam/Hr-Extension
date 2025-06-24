package com.eval.erp.service;

import com.eval.erp.model.Employee;
import com.eval.erp.model.Salary;
import com.eval.erp.model.SalaryComponent;

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
public class SalaryComponentService {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeService.class);

    private final ErpNextApiService erpNextApiService;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public SalaryComponentService(ErpNextApiService erpNextApiService) {
        this.erpNextApiService = erpNextApiService;
    }

    private List<SalaryComponent> convertIntoSalaryComponent(List<Map<String, Object>> componentData) {
        List<SalaryComponent> salaryComponents = new ArrayList<>();
        if (componentData == null) {
            logger.warn("No Salary Component data to convert");
            return salaryComponents;
        }

        for (Map<String, Object> data : componentData) {
            try {
                SalaryComponent component = new SalaryComponent();
                component.setName((String) data.get("salary_component"));
                component.setSalaryComponentAbbr((String) data.get("salary_component_abbr"));
                component.setType((String) data.get("type")); // Earning or Deduction
                component.setCompany((String) data.get("company"));
                component.setValeur((String) data.get("formula")); // Maps to 'valeur' in model
                // salaryStructure is not typically in ERPNext Salary Component; leave as null
                salaryComponents.add(component);
                logger.debug("Converted Salary Component: {}", component.getName());
            } catch (Exception e) {
                logger.warn("Failed to convert Salary Component data: {}. Skipping entry.", data, e);
            }
        }

        logger.info("Converted {} Salary Components", salaryComponents.size());
        return salaryComponents;
    }

    public List<SalaryComponent> getAllSalaryComponents(String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            logger.info("Fetching all Salary Components with sid: {}", sid);
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Component", fields, null, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from ERPNext API: {}", response);
                throw new Exception("Invalid response from ERPNext API");
            }
            List<Map<String, Object>> componentData = (List<Map<String, Object>>) response.getBody().get("data");
            return convertIntoSalaryComponent(componentData);
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error fetching Salary Components: {} - Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new Exception("HTTP error fetching Salary Components: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching Salary Components: {}", e.getMessage(), e);
            throw new Exception("Error fetching Salary Components: " + e.getMessage());
        }
    }

    public SalaryComponent getSalaryComponentByName(String name, String sid) throws Exception {
        try {
            SalaryComponent component= new SalaryComponent();
            String fields = "[\"*\"]";
            String filters = String.format("[[\"name\",\"=\",\"%s\"]]", name);
            logger.info("Fetching all Salary Components with sid: {}", sid);
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Component", fields, filters, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from ERPNext API: {}", response);
                throw new Exception("Invalid response from ERPNext API");
            }
            List<Map<String, Object>> componentData = (List<Map<String, Object>>) response.getBody().get("data");
            if (!componentData.isEmpty()) {
                component.setName((String) componentData.get(0).get("name"));
                component.setType((String) componentData.get(0).get("type"));
            }
            return component;

        } catch (HttpClientErrorException e) {
            logger.error("HTTP error fetching Salary Components: {} - Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new Exception("HTTP error fetching Salary Components: " + e.getMessage());
        } catch (Exception e) {
            logger.error("Error fetching Salary Components: {}", e.getMessage(), e);
            throw new Exception("Error fetching Salary Components: " + e.getMessage());
        }
    }


}