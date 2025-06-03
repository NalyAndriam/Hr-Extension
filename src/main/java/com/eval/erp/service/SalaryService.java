package com.eval.erp.service;

import com.eval.erp.model.Salary;
import com.eval.erp.model.SalarySummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.time.ZoneId;

@Service
public class SalaryService {

    private static final Logger logger = LoggerFactory.getLogger(SalaryService.class);

    @Autowired
    private ErpNextApiService erpNextApiService;

    @Autowired
    private UtilService utilService;

    public List<Salary> convertIntoSalaries(List<Map<String, Object>> salaryData) {
        List<Salary> salaries = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);

        for (Map<String, Object> data : salaryData) {
            Salary salary = new Salary();
            salary.setName((String) data.get("name"));
            salary.setEmployee((String) data.get("employee"));
            salary.setEmployeeName((String) data.get("employee_name"));
            salary.setGrossPay(data.get("gross_pay") != null ? ((Number) data.get("gross_pay")).doubleValue() : null);
            salary.setNetPay(data.get("net_pay") != null ? ((Number) data.get("net_pay")).doubleValue() : null);
            salary.setStatus((String) data.get("status"));
            salary.setTotalDeduction(data.get("total_deduction") != null ? ((Number) data.get("total_deduction")).doubleValue() : null);
            salary.setPayrollFrequency((String) data.get("payroll_frequency"));
            salary.setTotalInWords((String) data.get("total_in_words"));
            salary.setCompany((String) data.get("company"));
            salary.setDepartment((String) data.get("department"));
            salary.setDesignation((String) data.get("designation"));
            salary.setTotalWorkingDays(data.get("total_working_days") != null ? ((Number) data.get("total_working_days")).doubleValue() : null);
            salary.setPaymentDays(data.get("payment_days") != null ? ((Number) data.get("payment_days")).doubleValue() : null);
            salary.setCurrency((String) data.get("currency"));

            String postingDateStr = (String) data.get("posting_date");
            String startDateStr = (String) data.get("start_date");
            String endDateStr = (String) data.get("end_date");

            if (postingDateStr != null && !postingDateStr.isEmpty()) {
                try {
                    LocalDate postingDate = LocalDate.parse(postingDateStr, formatter);
                    salary.setYear(postingDate.getYear());
                    salary.setMonth(monthFormat.format(Date.from(postingDate.atStartOfDay(ZoneId.systemDefault()).toInstant())));
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

            if (startDateStr != null && !startDateStr.isEmpty()) {
                try {
                    salary.setStartDate(utilService.getFormattedDate(startDateStr));
                } catch (Exception e) {
                    logger.warn("Failed to format start_date for salary {}: {}", data.get("name"), e.getMessage());
                    salary.setStartDate(null);
                }
            }

            if (endDateStr != null && !endDateStr.isEmpty()) {
                try {
                    salary.setEndDate(utilService.getFormattedDate(endDateStr));
                } catch (Exception e) {
                    logger.warn("Failed to format end_date for salary {}: {}", data.get("name"), e.getMessage());
                    salary.setEndDate(null);
                }
            }

            logger.info("Salary Details: Name={}, Employee={}, EmployeeName={}, Month={}, Year={}, GrossPay={}, NetPay={}, Status={}, TotalDeduction={}, PayrollFrequency={}, TotalInWords={}", startDateStr, endDateStr, postingDateStr,
                    salary.getName(), salary.getEmployee(), salary.getEmployeeName(), salary.getMonth(),
                    salary.getYear(), salary.getGrossPay(), salary.getNetPay(), salary.getStatus(),
                    salary.getTotalDeduction(), salary.getPayrollFrequency(), salary.getTotalInWords());
                    salaries.add(salary);
        }
        return salaries;
    }

    public Salary getPayslipById(String payslipId, String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            String filters = "[[\"name\",\"=\",\"" + payslipId + "\"]]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, filters, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from payroll: {}", payslipId);
                throw new Exception("Invalid response from payroll API");
            }
            List<Map<String, Object>> salaryData = (List<Map<String, Object>>) response.getBody().get("data");
            if (salaryData.isEmpty()) {
                throw new Exception("Payslip not found: " + payslipId);
            }
            return convertIntoSalaries(salaryData).get(0);
        } catch (Exception e) {
            logger.error("Error retrieving payslip {}: {}", payslipId, e.getMessage(), e);
            throw new Exception("Error retrieving payslip: " + e.getMessage());
        }
    }


    public SalarySummary getSalariesByMonth(String month, String year, String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            String filters = "[]";
            if (month != null && !month.isEmpty() && year != null && !year.isEmpty()) {
                // Convert month name to number (e.g., "January" -> "01")
                String monthNum = String.format("%02d", Arrays.asList(
                    new SimpleDateFormat("MMMM", Locale.ENGLISH).getDateFormatSymbols().getMonths()
                ).indexOf(month) + 1);

                // Calculate the first and last day of the month
                String startDate = year + "-" + monthNum + "-01";
                String endDate = LocalDate.of(Integer.parseInt(year), Integer.parseInt(monthNum), 1)
                                        .withDayOfMonth(LocalDate.of(Integer.parseInt(year), Integer.parseInt(monthNum), 1)
                                        .lengthOfMonth())
                                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

                // Use 'between' operator for date range
                filters = "[[\"posting_date\",\"between\",[\"" + startDate + "\",\"" + endDate + "\"]]]";
            }

            ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, filters, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from ERPNext API for salaries: {}", response);
                throw new Exception("Invalid response from ERPNext API");
            }

            List<Map<String, Object>> salaryData = (List<Map<String, Object>>) response.getBody().get("data");
            List<Salary> salaries = convertIntoSalaries(salaryData);

            double totalGrossPay = 0.0;
            double totalDeductions = 0.0;
            double totalNetPay = 0.0;

            for (Salary salary : salaries) {
                if (salary.getGrossPay() != null) totalGrossPay += salary.getGrossPay();
                if (salary.getTotalDeduction() != null) totalDeductions += salary.getTotalDeduction();
                if (salary.getNetPay() != null) totalNetPay += salary.getNetPay();
            }

            logger.info("Fetched {} salaries for month {}, year {}: grossPay={}, deductions={}, netPay={}",
                salaries.size(), month != null ? month : "All", year != null ? year : "All", 
                totalGrossPay, totalDeductions, totalNetPay);

            return new SalarySummary(salaries, totalGrossPay, totalDeductions, totalNetPay);
        } catch (Exception e) {
            logger.error("Error fetching salaries for month {}, year {}: {}", month, year, e.getMessage(), e);
            throw new Exception("Error fetching salaries: " + e.getMessage());
        }
    }
}