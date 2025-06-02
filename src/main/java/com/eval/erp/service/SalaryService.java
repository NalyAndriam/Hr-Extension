package com.eval.erp.service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.eval.erp.model.Salary;

@Service
public class SalaryService {

    private static final Logger logger = LoggerFactory.getLogger(SalaryService.class);

    private final UtilService utilService;

    public SalaryService(UtilService utilService){
        this.utilService= utilService;
    }
    
    public List<Salary> convertIntoSalaries(List<Map<String, Object>> salaryData) {
        List<Salary> salaries = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH); // Ensure month is in English

        for (Map<String, Object> data : salaryData) {
            Salary salary = new Salary();
            salary.setName((String) data.get("name"));
            salary.setEmployee((String) data.get("employee"));
            salary.setEmployeeName((String) data.get("employee_name"));
            salary.setGrossPay(data.get("gross_pay") != null ? ((Number) data.get("gross_pay")).doubleValue() : null);
            salary.setNetPay(data.get("net_pay") != null ? ((Number) data.get("net_pay")).doubleValue() : null);
            salary.setStatus((String) data.get("status"));

            // Extract month and year from posting_date
            String postingDateStr = (String) data.get("posting_date");
            if (postingDateStr != null && !postingDateStr.isEmpty()) {
                try {
                    LocalDate postingDate = LocalDate.parse(postingDateStr, formatter);
                    salary.setYear(postingDate.getYear());
                    salary.setMonth(monthFormat.format(Date.from(postingDate.atStartOfDay(ZoneId.systemDefault()).toInstant())));
                    
                    // Set postingDate as java.util.Date
                    try {
                        salary.setPostingDate(utilService.getFormattedDate(postingDateStr));
                    } catch (Exception e) {
                        logger.warn("Failed to format posting_date for salary {}: {}", data.get("name"), e.getMessage());
                        salary.setPostingDate(null);
                    }
                } catch (DateTimeParseException e) {
                    logger.warn("Invalid posting_date format for salary {}: {}", data.get("name"), postingDateStr);
                    salary.setYear(null);
                    salary.setMonth(null);
                    salary.setPostingDate(null);
                }
            } else {
                logger.warn("posting_date is null or empty for salary {}", data.get("name"));
                salary.setYear(null);
                salary.setMonth(null);
                salary.setPostingDate(null);
            }

            // Log salary details
            logger.info("Salary Details: Name={}, Employee={}, EmployeeName={}, Month={}, Year={}, GrossPay={}, PostingDate={}",
                    salary.getName(), 
                    salary.getEmployee(), 
                    salary.getEmployeeName(), 
                    salary.getMonth(), 
                    salary.getYear(), 
                    salary.getGrossPay(), 
                    salary.getPostingDate());
            
            salaries.add(salary);
        }
        return salaries;
    }
}
