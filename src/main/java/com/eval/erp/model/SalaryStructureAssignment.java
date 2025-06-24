package com.eval.erp.model;

import java.util.HashMap;
import java.util.Map;

import com.eval.erp.service.UtilService;

public class SalaryStructureAssignment {
        private String employeeId;
        private String salaryStructure;
        private String fromDate;
        private String toDate;
        private double baseSalary;
        private String company;
        private UtilService utilService;

        // Getters, setters, and validation
        public void setUtilService(UtilService utilService) {
            this.utilService = utilService;
        }

        public void setEmployeeId(String employeeId) {
            this.employeeId = employeeId;
        }

        public void setSalaryStructure(String salaryStructure) {
            this.salaryStructure = salaryStructure;
        }

        public void setFromDate(String fromDate) {
            this.fromDate = fromDate;
        }

        public void setToDate(String toDate) {
            this.toDate = toDate;
        }

        public void setBaseSalary(double baseSalary) {
            this.baseSalary = baseSalary;
        }

        public void setCompany(String company) {
            this.company = company;
        }

        public String getEmployeeId() {
            return employeeId;
        }

        public String getSalaryStructure() {
            return salaryStructure;
        }

        public String getFromDate() {
            return fromDate;
        }

        public String getToDate() {
            return toDate;
        }

        public double getBaseSalary() {
            return baseSalary;
        }

        public String getCompany() {
            return company;
        }

        public void validate() {
            if (employeeId == null || employeeId.trim().isEmpty()) {
                throw new IllegalArgumentException("Employee ID is required");
            }
            if (salaryStructure == null || salaryStructure.trim().isEmpty()) {
                throw new IllegalArgumentException("Salary Structure is required");
            }
            if (fromDate == null || fromDate.trim().isEmpty()) {
                throw new IllegalArgumentException("From date is required");
            }
            if (baseSalary <= 0) {
                throw new IllegalArgumentException("Base salary must be positive");
            }
        }

        public Map<String, Object> toMap(boolean isUpdate) {
            Map<String, Object> map = new HashMap<>();
            if (!isUpdate) {
                map.put("doctype", "Salary Structure Assignment");
            }
            map.put("employee", employeeId);
            map.put("salary_structure", salaryStructure);
            map.put("from_date", fromDate);
            map.put("to_date", toDate);
            map.put("base", baseSalary);
            map.put("company", company);
            return map;
        }
}