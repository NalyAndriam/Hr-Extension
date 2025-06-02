package com.eval.erp.controller;

import com.eval.erp.model.Salary;
import com.eval.erp.service.SalaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpSession;

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
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/fiche-details";
            }
            logger.info("Session sid for payslip {}: {}", payslipId, sid);

            // Decode payslipId (replace _ with /)
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
}