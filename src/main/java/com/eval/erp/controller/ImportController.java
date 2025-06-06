package com.eval.erp.controller;

import com.eval.erp.model.SalaryStructure;
import com.eval.erp.service.ImportService;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

@Controller
public class ImportController {

    private static final Logger logger = LoggerFactory.getLogger(ImportController.class);

    @Autowired
    private ImportService importService;

    @Autowired
    private HttpSession session;

    @GetMapping("/import")
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

    @PostMapping("/import")
    public String importData(
            @RequestParam(value = "employeeCsvFile", required = false) MultipartFile employeeCsvFile,
            @RequestParam(value = "salaryStructureCsvFile", required = false) MultipartFile salaryStructureCsvFile,
            @RequestParam(value = "salarySlipCsvFile", required = false) MultipartFile salarySlipCsvFile,
            Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");
        List<String> importResults = new ArrayList<>();

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/import";
            }

            // Validate that at least one file is uploaded
            if ((employeeCsvFile == null || employeeCsvFile.isEmpty()) &&
                (salaryStructureCsvFile == null || salaryStructureCsvFile.isEmpty()) &&
                (salarySlipCsvFile == null || salarySlipCsvFile.isEmpty())) {
                model.addAttribute("error", "Please upload at least one valid CSV file.");
                return "pages/import";
            }

            // Validate file types
            if (employeeCsvFile != null && !employeeCsvFile.isEmpty() &&
                !isValidCsvFile(employeeCsvFile)) {
                model.addAttribute("error", "Invalid file type for Employee CSV. Please upload a CSV file.");
                return "pages/import";
            }
            if (salaryStructureCsvFile != null && !salaryStructureCsvFile.isEmpty() &&
                !isValidCsvFile(salaryStructureCsvFile)) {
                model.addAttribute("error", "Invalid file type for Salary Structure CSV. Please upload a CSV file.");
                return "pages/import";
            }
            if (salarySlipCsvFile != null && !salarySlipCsvFile.isEmpty() &&
                !isValidCsvFile(salarySlipCsvFile)) {
                model.addAttribute("error", "Invalid file type for Salary Slip CSV. Please upload a CSV file.");
                return "pages/import";
            }

            // Process Employee CSV
            if (employeeCsvFile != null && !employeeCsvFile.isEmpty()) {
                logger.info("Processing Employee CSV: {}", employeeCsvFile.getOriginalFilename());
                List<String> employeeResults = importService.importEmployeesFromCsv(employeeCsvFile, sid);
                importResults.addAll(employeeResults);
            }

            // Process Salary Structure CSV
            if (salaryStructureCsvFile != null && !salaryStructureCsvFile.isEmpty()) {
                logger.info("Processing Salary Structure CSV: {}", salaryStructureCsvFile.getOriginalFilename());
                List<SalaryStructure> salaryStructures = importService.importSalaryStructure(salaryStructureCsvFile, sid);
                for (SalaryStructure salaryStructure : salaryStructures) {
                    importResults.addAll(salaryStructure.getResults());
                }
            }

            // Process Salary Slip CSV
            if (salarySlipCsvFile != null && !salarySlipCsvFile.isEmpty()) {
                logger.info("Processing Salary Slip CSV: {}", salarySlipCsvFile.getOriginalFilename());
                List<String> salarySlipResults = importService.importSalarySlipsFromCsv(salarySlipCsvFile, sid);
                importResults.addAll(salarySlipResults);
            }

            model.addAttribute("importResults", importResults);
            logger.info("Import completed: {} results", importResults.size());

        } catch (Exception e) {
            logger.error("Error importing data: {}", e.getMessage());
            model.addAttribute("error", "Error during import: " + e.getMessage());
        }

        return "pages/import";
    }

    private boolean isValidCsvFile(MultipartFile file) {
        return file.getContentType() != null &&
               (file.getContentType().equals("text/csv") ||
                file.getContentType().equals("application/vnd.ms-excel"));
    }
}