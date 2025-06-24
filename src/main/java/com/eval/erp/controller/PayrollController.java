package com.eval.erp.controller;

import com.eval.erp.model.SalaryComponent;
import com.eval.erp.model.SalarySlip;
import com.eval.erp.model.SalaryStructure;
import com.eval.erp.model.SalaryStructureAssignment;
import com.eval.erp.service.EmployeeService;
import com.eval.erp.service.PayrollService;
import com.eval.erp.service.SalaryComponentService;
import com.eval.erp.service.UtilService;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

@Controller
public class PayrollController {

    private static final Logger logger = LoggerFactory.getLogger(PayrollController.class);

    private final EmployeeService employeeService;
    private final PayrollService payrollService;
    private final UtilService utilService;
    private final SalaryComponentService salaryComponentService;
    private final HttpSession session;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    @Autowired
    public PayrollController(EmployeeService employeeService, PayrollService payrollService, UtilService utilService, HttpSession session,
                            SalaryComponentService salaryComponentService) {
        this.employeeService = employeeService;
        this.payrollService = payrollService;
        this.utilService = utilService;
        this.salaryComponentService= salaryComponentService;
        this.session = session;
    }

    @GetMapping("/salaries/generate")
    public String showSalariesGeneratePage(Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "generate");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/home";
            }

            List employees = employeeService.getAllEmployees(sid);
            model.addAttribute("employees", employees);

        } catch (Exception e) {
            logger.error("Error loading dashboard data: {}", e.getMessage());
            model.addAttribute("error", "Error loading employees: " + e.getMessage());
        }

        return "pages/generer-salaire";
    }

    @PostMapping("/salaries/generate")
    public String generateSalaries(Model model,
                                @RequestParam String employeeId,
                                @RequestParam("monthStart") String monthStart,
                                @RequestParam("monthEnd") String monthEnd,
                                @RequestParam(required = false) Double amount) {

        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "generate");

        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();
        List<String> infos = new ArrayList<>();

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/home";
            }

            List employees = employeeService.getAllEmployees(sid);
            model.addAttribute("employees", employees);

            SalaryStructure salaryStructure = payrollService.getSalaryStructure(sid);
            if (salaryStructure == null) {
                model.addAttribute("error", "No salary structure found.");
                return "pages/generer-salaire";
            }

            if (amount == null) {
                String formattedMonthStart = utilService.convertMonthYearToFullDate(monthStart);
                formattedMonthStart = utilService.formatDate(utilService.getFormattedDate(formattedMonthStart), "yyyy-MM-dd");
                SalaryStructureAssignment last = payrollService.getLastAssignmentBeforeMonth(employeeId, formattedMonthStart, sid);
                if (last == null) {
                    model.addAttribute("error", "No salary assignment found before the entered month.");
                    return "pages/generer-salaire";
                }
                amount = last.getBaseSalary();
            }

            YearMonth start = YearMonth.parse(monthStart);
            YearMonth end = YearMonth.parse(monthEnd);

            if (start.isAfter(end)) {
                model.addAttribute("error", "Start month cannot be after end month.");
                return "pages/generer-salaire";
            }

            YearMonth current = start;
            boolean anyGenerated = false;

            while (!end.isBefore(current)) {
                logger.info("Processing month: {}", current);
                String currentDate = utilService.convertMonthYearToFullDate(current.toString());
                try {
                    boolean salaryExists = payrollService.checkSalaryStructureAssignmentExists(employeeId, salaryStructure.getSalaryStructureName(), currentDate, sid);
                    if (!salaryExists) {
                        SalarySlip salarySlip = new SalarySlip(currentDate, employeeId, amount, salaryStructure.getSalaryStructureName());
                        salarySlip.setUtilService(utilService);
                        int insert = payrollService.insertSalarySlip(salarySlip, sid, session);
                        if (insert==1) {
                            successes.add("Salary Slip generated for " + current);
                            anyGenerated = true;
                        } else if (insert==2) {
                            successes.add("Skipping Salary Slip generation in month:"+ current +" Assignment already exists");
                        }
                    } else {
                        errors.add("Failed to generate Salary Slip for " + current);
                    }
                } catch (Exception e) {
                    logger.error("Error processing month {}: {}", current, e.getMessage());
                    errors.add("Failed to generate Salary Slip for " + current + ": " + e.getMessage());
                }
                current = current.plusMonths(1);
            }

            if (!successes.isEmpty()) {
                model.addAttribute("success", String.join("; ", successes));
            }
            if (!errors.isEmpty()) {
                model.addAttribute("error", String.join("; ", errors));
            }
            if (!infos.isEmpty()) {
                model.addAttribute("info", String.join("; ", infos));
            }
            if (!anyGenerated && infos.isEmpty()) {
                model.addAttribute("info", "No new Salary Slips generated.");
            }

        } catch (Exception e) {
            logger.error("Unexpected error generating salaries: {}", e.getMessage(), e);
            model.addAttribute("error", "Unexpected error: " + e.getMessage());
        }

        return "pages/generer-salaire";
    }


    @GetMapping("/salaries/modif")
    public String showModifSalaryPage(Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "modif");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/home";
            }

            List<SalaryComponent> components = salaryComponentService.getAllSalaryComponents(sid);
            model.addAttribute("components", components);

        } catch (Exception e) {
            logger.error("Error loading dashboard data: {}", e.getMessage());
            model.addAttribute("error", "Error loading employees: " + e.getMessage());
        }

        return "pages/modif-salaire";
    }

    @PostMapping("/salaries/modif")
    public String updateSalaries(Model model,
                                @RequestParam("component") String component,
                                @RequestParam("comparison") String comparison,
                                @RequestParam("amount") Double amount,
                                @RequestParam("percentage") Double percentage) {

        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "modif");

        List<String> errors = new ArrayList<>();
        List<String> successes = new ArrayList<>();
        List<String> infos = new ArrayList<>();

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/home";
            }

            List<SalaryComponent> components = salaryComponentService.getAllSalaryComponents(sid);
            model.addAttribute("components", components);

            List<SalarySlip> salaries = payrollService.filterSalarySlipsByComponent(component, amount, comparison, sid);
            if (salaries.isEmpty()) {
                infos.add("No Salary Slips found matching the criteria: component=" + component + ", comparison=" + comparison + ", amount=" + amount);
            } else {
                for (SalarySlip salary : salaries) {
                    try {
                        SalarySlip duplicated= salary;
                        boolean deleted = payrollService.deleteSalarySlip(salary.getName(), sid);
                        if (deleted) {
                            double newBaseSalary = utilService.getPourcentage(duplicated.getBaseSalary(), percentage);
                            salary.setBaseSalary(newBaseSalary);
                            int result = payrollService.insertSalarySlip(duplicated, sid, session);
                            if (result == 1) {
                                successes.add("Updated Salary Slip " + duplicated.getName() + " with new base salary: " + newBaseSalary);
                            } else if (result == 2) {
                                infos.add("Salary Slip " + duplicated.getName() + " already exists, skipped.");
                            }
                        } else {
                            errors.add("Failed to delete Salary Slip " + duplicated.getName());
                        }
                    } catch (Exception e) {
                        errors.add("Error updating Salary Slip " + salary.getName() + ": " + e.getMessage());
                    }
                }
            }

            if (!successes.isEmpty()) {
                model.addAttribute("success", String.join("; ", successes));
            }
            if (!errors.isEmpty()) {
                model.addAttribute("error", String.join("; ", errors));
            }
            if (!infos.isEmpty()) {
                model.addAttribute("info", String.join("; ", infos));
            }
            if (successes.isEmpty() && errors.isEmpty() && infos.isEmpty()) {
                model.addAttribute("info", "No actions performed. Please check the input criteria.");
            }

        } catch (Exception e) {
            logger.error("Unexpected error updating salaries: {}", e.getMessage(), e);
            model.addAttribute("error", "Unexpected error: " + e.getMessage());
        }

        return "pages/modif-salaire";
    }

}