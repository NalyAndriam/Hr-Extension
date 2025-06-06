package com.eval.erp.controller;

import com.eval.erp.model.Salary;
import com.eval.erp.model.SalarySummary;
import com.eval.erp.model.SalaryTotal;
import com.eval.erp.service.SalaryService;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.servlet.http.HttpSession;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class SalaryController {

    private static final Logger logger = LoggerFactory.getLogger(SalaryController.class);

    private final SalaryService salaryService;
    private final HttpSession session;

    @Value("${erpnext.api.url}")
    private String frappeApiUrl;

    public SalaryController(SalaryService salaryService, HttpSession session) {
        this.salaryService = salaryService;
        this.session = session;
    }

    @GetMapping("/employee/{employeeId}/payslip/{payslipId}")
    public String getPayslipDetails(@PathVariable String employeeId, @PathVariable String payslipId, Model model) {
        logger.info("Accessing payslip details for employeeId: {}, payslipId: {}", employeeId, payslipId);
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "employees");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                logger.warn("ERPNext session not found for payslip {}. Session sid: null", payslipId);
                model.addAttribute("error", "Session ERPNext non trouvée. Veuillez vous reconnecter.");
                return "pages/fiche-details";
            }
            logger.info("Session sid for payslip {}: {}", payslipId, sid);

            String decodedPayslipId = payslipId.replace("_", "/");
            logger.info("Decoded payslipId: {}", decodedPayslipId);

            Salary payslip = salaryService.getPayslipById(decodedPayslipId, sid);
            model.addAttribute("payslip", payslip);
            model.addAttribute("employeeId", employeeId);
            logger.info("Fetched payslip details for ID: {}", decodedPayslipId);
        } catch (Exception e) {
            logger.error("Error fetching payslip {}: {}", payslipId, e.getMessage());
            model.addAttribute("error", e.getMessage());
        }
        return "pages/fiche-details";
    }

    @GetMapping("/salaries")
    public String getSalaryList(@RequestParam(required = false) String monthYear, Model model) {
        logger.info("Accessing salary list for monthYear: {}", monthYear);
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "salaries");

        String month = null;
        String year = null;
        if (monthYear != null && !monthYear.isEmpty()) {
            try {
                String[] parts = monthYear.split("-");
                if (parts.length == 2) {
                    year = parts[0];
                    int monthNum = Integer.parseInt(parts[1]);
                    month = new SimpleDateFormat("MMMM", Locale.ENGLISH)
                        .getDateFormatSymbols().getMonths()[monthNum - 1];
                }
            } catch (Exception e) {
                logger.warn("Invalid monthYear format: {}", monthYear);
                model.addAttribute("error", "Invalid month and year format");
            }
        }

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                logger.warn("ERPNext session not found for salary list. Session sid: null");
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/salary-list";
            }
            logger.info("Session sid for salary list: {}", sid);

            List<Salary> salaries = salaryService.getSalariesByMonth(month, year, sid);
            model.addAttribute("salaries", salaries);
            model.addAttribute("monthYear", monthYear);
            logger.info("Fetched {} salaries for month: {}, year: {}", salaries.size(), 
                        month != null ? month : "All", year != null ? year : "All");
        } catch (Exception e) {
            logger.error("Error fetching salary list for month {}, year {}: {}", month, year, e.getMessage());
            model.addAttribute("error", e.getMessage());
        }
        return "pages/salary-list";
    }

    @GetMapping("/salaries/total")
    public String getSalaryTotals(@RequestParam(required = false) String year, Model model) {
        logger.info("Accessing salary totals for year: {}", year);
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "salary-total");

        // List of years for the filter
        List<String> years = IntStream.rangeClosed(2000, java.time.Year.now().getValue())
                .mapToObj(String::valueOf)
                .sorted((a, b) -> b.compareTo(a))
                .collect(Collectors.toList());
        model.addAttribute("years", years);

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                logger.warn("ERPNext session not found for salary totals. Session sid: null");
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/salary-total";
            }
            logger.info("Session sid for salary totals: {}", sid);

            List<SalaryTotal> salaryTotals = salaryService.getSalaryTotalsByYear(year, sid);
            model.addAttribute("salaryTotals", salaryTotals);
            model.addAttribute("year", year);
            logger.info("Fetched {} salary totals for year: {}", salaryTotals.size(), year != null ? year : "All");
        } catch (Exception e) {
            logger.error("Error fetching salary totals for year {}: {}", year, e.getMessage());
            model.addAttribute("error", e.getMessage());
        }
        return "pages/salary-total";
    }

    @GetMapping("/salaries/chart")
    public String getSalaryChart(@RequestParam(required = false) String year, Model model) {
        logger.info("Accessing salary chart for year: {}", year);
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "salary-chart");

        List<String> years = IntStream.rangeClosed(2000, java.time.Year.now().getValue())
                .mapToObj(String::valueOf)
                .sorted((a, b) -> b.compareTo(a))
                .collect(Collectors.toList());
        model.addAttribute("years", years);

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                logger.warn("ERPNext session not found for salary chart. Session sid: null");
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/salary-chart";
            }
            logger.info("Session sid for salary chart: {}", sid);

            Map<String, Object> chartData = salaryService.getSalaryChartData(year, sid);
            // Serialize chartData to JSON
            ObjectMapper objectMapper = new ObjectMapper();
            String chartDataJson = objectMapper.writeValueAsString(chartData);
            model.addAttribute("chartDataJson", chartDataJson); // Pass JSON string
            model.addAttribute("chartData", chartData); // Keep for Thymeleaf conditions
            model.addAttribute("year", year);
            logger.info("Prepared chart data for year: {}", year != null ? year : "All");
        } catch (Exception e) {
            logger.error("Error fetching salary chart data for year {}: {}", year, e.getMessage());
            model.addAttribute("error", e.getMessage());
        }
        return "pages/salary-chart";
    }

}