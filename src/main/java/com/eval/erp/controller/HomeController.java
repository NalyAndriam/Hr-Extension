package com.eval.erp.controller;

import com.eval.erp.model.SalarySummary;
import com.eval.erp.service.EmployeeService;
import com.eval.erp.service.SalaryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;
import java.time.Year;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class HomeController {

    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);

    private final EmployeeService employeeService;
    private final SalaryService salaryService;
    private final HttpSession session;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public HomeController(EmployeeService employeeService, SalaryService salaryService, HttpSession session) {
        this.employeeService = employeeService;
        this.salaryService = salaryService;
        this.session = session;
    }

    @GetMapping("/home")
    public String getDashboard(Model model, @RequestParam(required = false) String year) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "home");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/home";
            }

            // Total Employees
            List employees = employeeService.getAllEmployees(sid);
            model.addAttribute("totalEmployees", employees.size());

            // Salary Totals for Current Year
            String selectedYear = (year != null && !year.isEmpty()) ? year : String.valueOf(Year.now().getValue());
            List salaryTotals = salaryService.getSalaryTotalsByYear(selectedYear, sid);
            double totalGrossPay = salaryTotals.stream()
                    .mapToDouble(st -> ((com.eval.erp.model.SalaryTotal) st).getTotalGrossPay())
                    .sum();
            double avgNetPay = salaryTotals.stream()
                    .mapToDouble(st -> ((com.eval.erp.model.SalaryTotal) st).getTotalNetPay())
                    .average()
                    .orElse(0.0);
            model.addAttribute("totalGrossPay", totalGrossPay);
            model.addAttribute("avgNetPay", avgNetPay);

            // Recent Salaries (limit to 5)
            SalarySummary salarySummary = salaryService.getSalarySummaryByMonth(null, null, sid);
            List recentSalaries = salarySummary.getSalaries().stream().limit(5).collect(Collectors.toList());
            model.addAttribute("recentSalaries", recentSalaries);

            // Chart Data
            ObjectMapper objectMapper = new ObjectMapper();
            Map chartData = salaryService.getSalaryChartData(selectedYear, sid);
            String chartDataJson = objectMapper.writeValueAsString(chartData);
            model.addAttribute("chartDataJson", chartDataJson);
            model.addAttribute("chartData", chartData);

            // Years for Dropdown
            List years = IntStream.rangeClosed(2000, Year.now().getValue())
                    .mapToObj(String::valueOf)
                    .sorted((a, b) -> b.compareTo(a))
                    .collect(Collectors.toList());
            model.addAttribute("years", years);

        } catch (Exception e) {
            logger.error("Error loading dashboard data: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
        }
        return "pages/home";
    }

    @GetMapping("/test")
    public String showTestPage() {
        return "pages/insert-test";
    }
}