package com.eval.erp.controller;

import com.eval.erp.model.Department;
import com.eval.erp.model.Employee;
import com.eval.erp.service.DepartmentService;
import com.eval.erp.service.EmployeeService;
import com.eval.erp.service.StatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;

@Controller

public class EmployeeController {

    private static final Logger logger = LoggerFactory.getLogger(EmployeeController.class);

    private final EmployeeService employeeService;
    private final DepartmentService departmentService;
    private final StatusService statusService;
    private final HttpSession session;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public EmployeeController(EmployeeService employeeService, DepartmentService departmentService, 
                              StatusService statusService, HttpSession session) {
        this.employeeService = employeeService;
        this.departmentService= departmentService;
        this.statusService= statusService;
        this.session= session;
    }

    @GetMapping("/employee")
    public String getEmployees(
            @RequestParam Optional<String> name,
            @RequestParam Optional<String> department,
            @RequestParam Optional<String> status,
            Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "employees");

        try {

            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/employee-list";
            }

            List<Department> departments = departmentService.getAllDepartments(sid);
            List<String> statuses = statusService.getAllStatuses();
            model.addAttribute("departments", departments);
            model.addAttribute("statuses", statuses);

            model.addAttribute("nameFilter", name.orElse(""));
            model.addAttribute("departmentFilter", department.orElse(""));
            model.addAttribute("statusFilter", status.orElse(""));

            List<Employee> employees;
            if (name.isEmpty() && department.isEmpty() && status.isEmpty()) {
                employees = employeeService.getAllEmployees(sid);
            } else {
                employees = employeeService.searchEmployees(name, department, status, sid);
            }
            model.addAttribute("employees", employees);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "pages/employee-list";
    }
}