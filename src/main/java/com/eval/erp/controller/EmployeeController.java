package com.eval.erp.controller;

import com.eval.erp.model.Department;
import com.eval.erp.model.Employee;
import com.eval.erp.model.Gender;
import com.eval.erp.model.Salary;
import com.eval.erp.service.DepartmentService;
import com.eval.erp.service.EmployeeService;
import com.eval.erp.service.GenderService;
import com.eval.erp.service.StatusService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
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
    private final GenderService genderService;
    private final HttpSession session;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public EmployeeController(EmployeeService employeeService, DepartmentService departmentService, 
                              StatusService statusService, GenderService genderService, HttpSession session) {
        this.employeeService = employeeService;
        this.departmentService = departmentService;
        this.statusService = statusService;
        this.genderService = genderService;
        this.session = session;
    }

    @GetMapping("/employee")
    public String getEmployees(
            @RequestParam Optional<String> name,
            @RequestParam Optional<String> id,
            @RequestParam Optional<String> department,
            @RequestParam Optional<String> status,
            @RequestParam Optional<String> gender,
            @RequestParam Optional<String> dateOfJoining,
            @RequestParam Optional<String> dateOfBirth,
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
            List<Gender> genders = genderService.getAllGenders(sid);
            model.addAttribute("departments", departments);
            model.addAttribute("statuses", statuses);
            model.addAttribute("genders", genders);

            model.addAttribute("nameFilter", name.orElse(""));
            model.addAttribute("idFilter", id.orElse(""));
            model.addAttribute("departmentFilter", department.orElse(""));
            model.addAttribute("statusFilter", status.orElse(""));
            model.addAttribute("genderFilter", gender.orElse(""));
            model.addAttribute("dateOfJoiningFilter", dateOfJoining.orElse(""));
            model.addAttribute("dateOfBirthFilter", dateOfBirth.orElse(""));

            List<Employee> employees;
            if (name.isEmpty() && id.isEmpty() && department.isEmpty() && status.isEmpty() && gender.isEmpty() 
                && dateOfJoining.isEmpty() && dateOfBirth.isEmpty()) {
                employees = employeeService.getAllEmployees(sid);
            } else {
                employees = employeeService.searchEmployees(name, id, department, status, gender, dateOfJoining, dateOfBirth, sid);
            }
            model.addAttribute("employees", employees);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "pages/employee-list";
    }

    @GetMapping("/employee/{id}")
    public String getEmployeeDetails(@PathVariable String id, Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "employees");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/employee-fiche";
            }

            Employee employee = employeeService.getEmployeeById(id, sid);
            List<Salary> salaries = employeeService.getEmployeeSalaries(id, sid);

            model.addAttribute("employee", employee);
            model.addAttribute("salaries", salaries);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "pages/employee-fiche";
    }

    @PostMapping("/employee/import")
    public String importEmployees(@RequestParam("csvFile") MultipartFile file, Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/import";
            }

            if (file.isEmpty()) {
                model.addAttribute("error", "Please upload a valid CSV file.");
                return "pages/import";
            }

            if (!file.getContentType().equals("text/csv") && !file.getContentType().equals("application/vnd.ms-excel")) {
                model.addAttribute("error", "Invalid file type. Please upload a CSV file.");
                return "pages/import";
            }

            List<String> importResults = employeeService.importEmployeesFromCsv(file, sid);
            model.addAttribute("importResults", importResults);

        } catch (Exception e) {
            logger.error("Error importing employees: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
        }

        return "pages/import";
    }

    @GetMapping("/employee/import")
    public String showImportPage(Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/import";
            }

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "pages/import";
    }
}