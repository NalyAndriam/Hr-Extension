package com.eval.erp.service;

import com.eval.erp.model.Component;
import com.eval.erp.model.Salary;
import com.eval.erp.model.SalarySlip;
import com.eval.erp.model.SalaryStructureAssignment;
import com.eval.erp.model.SalarySummary;
import com.eval.erp.model.SalaryTotal;
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
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.time.ZoneId;

@Service
public class SalaryService {

    private static final Logger logger = LoggerFactory.getLogger(SalaryService.class);

    @Autowired
    private ErpNextApiService erpNextApiService;

    @Autowired
    private UtilService utilService;

    @Autowired
    private ValidationService validationService;

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

            if (data.get("earnings") != null) {
                List<Map<String, Object>> earningsData = (List<Map<String, Object>>) data.get("earnings");
                logger.info("Earnings for salary {}: {}", data.get("name"), earningsData);
                List<Component> earnings = new ArrayList<>();
                for (Map<String, Object> earning : earningsData) {
                    Component component = new Component();
                    component.setDescription((String) earning.get("salary_component"));
                    component.setAmount(earning.get("amount") != null ? ((Number) earning.get("amount")).doubleValue() : null);
                    earnings.add(component);
                }
                salary.setEarnings(earnings);
            } else {
                logger.warn("No earnings data for salary {}", data.get("name"));
            }

            if (data.get("deductions") != null) {
                List<Map<String, Object>> deductionsData = (List<Map<String, Object>>) data.get("deductions");
                logger.info("Deductions for salary {}: {}", data.get("name"), deductionsData);
                List<Component> deductions = new ArrayList<>();
                for (Map<String, Object> deduction : deductionsData) {
                    Component component = new Component();
                    component.setDescription((String) deduction.get("salary_component"));
                    component.setAmount(deduction.get("amount") != null ? ((Number) deduction.get("amount")).doubleValue() : null);
                    deductions.add(component);
                }
                salary.setDeductions(deductions);
            } else {
                logger.warn("No deductions data for salary {}", data.get("name"));
            }

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

            logger.info("Salary Details: Name={}, Employee={}, EmployeeName={}, Month={}, Year={}, GrossPay={}, NetPay={}, Status={}, TotalDeduction={}, PayrollFrequency={}, TotalInWords={}",
                    salary.getName(), salary.getEmployee(), salary.getEmployeeName(), salary.getMonth(),
                    salary.getYear(), salary.getGrossPay(), salary.getNetPay(), salary.getStatus(),
                    salary.getTotalDeduction(), salary.getPayrollFrequency(), salary.getTotalInWords());
            salaries.add(salary);
        }
        return salaries;
    }

    public Salary getPayslipById(String payslipId, String sid) throws Exception {
        try {
            String resourcePath = "Salary Slip/" + payslipId;
            ResponseEntity<Map> response = erpNextApiService.getResourceById(resourcePath, sid);
            if (response.getBody() == null || !response.getBody().containsKey("data")) {
                logger.error("Invalid response from payroll for payslip: {}", payslipId);
                throw new Exception("Invalid response from payroll API");
            }

            Map<String, Object> salaryData = (Map<String, Object>) response.getBody().get("data");
            logger.info("Salary data fetched for payslip {}: {}", payslipId, salaryData);

            List<Map<String, Object>> salaryDataList = List.of(salaryData);
            List<Salary> salaries = convertIntoSalaries(salaryDataList);
            if (salaries.isEmpty()) {
                throw new Exception("Payslip not found: " + payslipId);
            }

            Salary payslip = salaries.get(0);
            logger.info("Payslip parsed: {}, Earnings: {}, Deductions: {}", 
                        payslip.getName(), payslip.getEarnings(), payslip.getDeductions());
            return payslip;
        } catch (Exception e) {
            logger.error("Error retrieving payslip {}: {}", payslipId, e.getMessage(), e);
            throw new Exception("Error retrieving payslip: " + e.getMessage());
        }
    }

    public SalarySummary getSalarySummaryByMonth(String month, String year, String sid) throws Exception {
    try {
        String fields = "[\"name\"]";
        String filters = "";
        if (month != null && !month.isEmpty() && year != null && !year.isEmpty()) {
            String monthNum = String.format("%02d", Arrays.asList(
                    new SimpleDateFormat("MMMM", Locale.ENGLISH).getDateFormatSymbols().getMonths())
                    .indexOf(month) + 1);

            String startDate = year + "-" + monthNum + "-01";
            String endDate = LocalDate.of(Integer.parseInt(year), Integer.parseInt(monthNum), 1)
                    .withDayOfMonth(LocalDate.of(Integer.parseInt(year), Integer.parseInt(monthNum), 1).lengthOfMonth())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            filters = String.format("[[\"posting_date\",\"between\",[\"%s\", \"%s\"]]]", startDate, endDate);
        }

        ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, filters, sid);
        if (response.getBody() == null || !response.getBody().containsKey("data")) {
            logger.error("Invalid response from ERPNext API for salaries: {}", response);
            throw new Exception("Invalid response from ERPNext API");
        }

        List<Map<String, Object>> salaryData = (List<Map<String, Object>>) response.getBody().get("data");
        List<Salary> salaries = new ArrayList<>();
        double totalGrossPay = 0.0;
        double totalDeductions = 0.0;
        double totalNetPay = 0.0;

        for (Map<String, Object> data : salaryData) {
            String payslipId = (String) data.get("name");
            try {
                Salary payslip = getPayslipById(payslipId, sid);
                salaries.add(payslip);

                if (payslip.getGrossPay() != null) totalGrossPay += payslip.getGrossPay();
                if (payslip.getTotalDeduction() != null) totalDeductions += payslip.getTotalDeduction();
                if (payslip.getNetPay() != null) totalNetPay += payslip.getNetPay();
            } catch (Exception e) {
                logger.error("Error fetching details for payslip {}: {}", payslipId, e.getMessage());
                continue;
            }
        }

        logger.info("Fetched {} salaries with totals for month {}, year {}: GrossPay={}, Deductions={}, NetPay={}", 
                    salaries.size(), month != null ? month : "All", year != null ? year : "All",
                    totalGrossPay, totalDeductions, totalNetPay);

        return new SalarySummary(salaries, totalGrossPay, totalDeductions, totalNetPay);
    } catch (Exception e) {
        logger.error("Error fetching salary summary for month {}, year {}: {}", month, year, e.getMessage(), e);
        throw new Exception("Error fetching salary summary: " + e.getMessage());
    }
}

    public List<SalaryTotal> getSalaryTotalsByYear(String year, String sid) throws Exception {
    try {
        String fields = "[\"name\"]";
        String filters = "";
        if (year != null && !year.isEmpty()) {
            String startDate = year + "-01-01";
            String endDate = year + "-12-31";
            filters = String.format("[[\"posting_date\",\"between\",[\"%s\", \"%s\"]]]", startDate, endDate);
        }

        // Add limit_page_length to restrict to 100 rows
        ResponseEntity<Map> response = erpNextApiService.getResource("Salary Slip", fields, filters, sid, 100);
        if (response.getBody() == null || !response.getBody().containsKey("data")) {
            logger.error("Invalid response from ERPNext API for salary totals: {}", response);
            throw new Exception("Invalid response from ERPNext API");
        }

        List<Map<String, Object>> salaryData = (List<Map<String, Object>>) response.getBody().get("data");
        List<Salary> salaries = new ArrayList<>();

        for (Map<String, Object> data : salaryData) {
            String payslipId = (String) data.get("name");
            try {
                Salary payslip = getPayslipById(payslipId, sid);
                salaries.add(payslip);
            } catch (Exception e) {
                logger.error("Error fetching details for payslip {}: {}", payslipId, e.getMessage());
                continue;
            }
        }

        Map<String, List<Salary>> salariesByMonth = salaries.stream()
                .filter(s -> s.getMonth() != null && s.getYear() != null)
                .collect(Collectors.groupingBy(
                        s -> s.getMonth() + "-" + s.getYear(),
                        Collectors.toList()
                ));

        List<SalaryTotal> salaryTotals = new ArrayList<>();
        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);

        for (Map.Entry<String, List<Salary>> entry : salariesByMonth.entrySet()) {
            String[] parts = entry.getKey().split("-");
            String month = parts[0];
            Integer yearNum = Integer.parseInt(parts[1]);

            List<Salary> monthSalaries = entry.getValue();
            double totalGrossPay = 0.0;
            double totalDeductions = 0.0;
            double totalNetPay = 0.0;
            Map<String, Double> componentTotals = new HashMap<>();

            for (Salary salary : monthSalaries) {
                if (salary.getGrossPay() != null) totalGrossPay += salary.getGrossPay();
                if (salary.getTotalDeduction() != null) totalDeductions += salary.getTotalDeduction();
                if (salary.getNetPay() != null) totalNetPay += salary.getNetPay();

                if (salary.getEarnings() != null) {
                    for (Component earning : salary.getEarnings()) {
                        if (earning.getDescription() != null && earning.getAmount() != null) {
                            componentTotals.merge(
                                    "Earning: " + earning.getDescription(),
                                    earning.getAmount(),
                                    Double::sum
                            );
                        }
                    }
                }

                if (salary.getDeductions() != null) {
                    for (Component deduction : salary.getDeductions()) {
                        if (deduction.getDescription() != null && deduction.getAmount() != null) {
                            componentTotals.merge(
                                    "Deduction: " + deduction.getDescription(),
                                    deduction.getAmount(),
                                    Double::sum
                            );
                        }
                    }
                }
            }

            logger.info("Totals for {} {}: GrossPay={}, Deductions={}, NetPay={}, ComponentTotals={}, Salaries={}", 
                        month, yearNum, totalGrossPay, totalDeductions, totalNetPay, componentTotals, monthSalaries.size());

            SalaryTotal salaryTotal = new SalaryTotal(month, yearNum, totalGrossPay, totalDeductions, totalNetPay, monthSalaries, componentTotals);
            salaryTotals.add(salaryTotal);
        }

        salaryTotals.sort((a, b) -> {
            int yearCompare = a.getYear().compareTo(b.getYear());
            if (yearCompare != 0) return yearCompare;
            return Arrays.asList(monthFormat.getDateFormatSymbols().getMonths())
                    .indexOf(a.getMonth()) - Arrays.asList(monthFormat.getDateFormatSymbols().getMonths())
                    .indexOf(b.getMonth());
        });

        logger.info("Fetched {} salary totals for year {} (limited to 100 salary slips)", salaryTotals.size(), year != null ? year : "All");
        return salaryTotals;
    } catch (Exception e) {
        logger.error("Error fetching salary totals for year {}: {}", year, e.getMessage(), e);
        throw new Exception("Error fetching salary totals: " + e.getMessage());
    }
}

    public Map<String, Object> getSalaryChartData(String year, String sid) throws Exception {
        try {
            List<SalaryTotal> salaryTotals = getSalaryTotalsByYear(year, sid);
            Map<String, Object> chartData = new HashMap<>();
            List<String> months = new ArrayList<>();
            List<Map<String, Object>> datasets = new ArrayList<>();

            SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
            List<String> monthOrder = Arrays.asList(monthFormat.getDateFormatSymbols().getMonths());

            Set<String> allComponents = new TreeSet<>();
            for (SalaryTotal total : salaryTotals) {
                if (total.getComponentTotals() != null) {
                    allComponents.addAll(total.getComponentTotals().keySet());
                }
            }

            List<Double> grossPays = new ArrayList<>(Collections.nCopies(12, 0.0));
            List<Double> deductions = new ArrayList<>(Collections.nCopies(12, 0.0));
            List<Double> netPays = new ArrayList<>(Collections.nCopies(12, 0.0));
            Map<String, List<Double>> componentSeries = new HashMap<>();
            for (String component : allComponents) {
                componentSeries.put(component, new ArrayList<>(Collections.nCopies(12, 0.0)));
            }

            for (int i = 0; i < 12; i++) {
                String month = monthOrder.get(i);
                months.add(month);

                Optional<SalaryTotal> matchingTotal = salaryTotals.stream()
                        .filter(total -> total.getMonth().equals(month) && 
                                (year == null || total.getYear().toString().equals(year)))
                        .findFirst();

                if (matchingTotal.isPresent()) {
                    SalaryTotal total = matchingTotal.get();
                    grossPays.set(i, total.getTotalGrossPay());
                    deductions.set(i, total.getTotalDeductions());
                    netPays.set(i, total.getTotalNetPay());

                    for (String component : allComponents) {
                        Double amount = total.getComponentTotals().getOrDefault(component, 0.0);
                        componentSeries.get(component).set(i, amount);
                    }
                }
            }

            datasets.add(createDataset("Gross Salary", grossPays, "#28a745", "rgba(40, 167, 69, 0.2)", false));
            datasets.add(createDataset("Deductions", deductions, "#dc3545", "rgba(220, 53, 69, 0.2)", false));
            datasets.add(createDataset("Net Salary", netPays, "#007bff", "rgba(0, 123, 255, 0.2)", false));

            int colorIndex = 0;
            String[] colors = {"#17a2b8", "#ffc107", "#6f42c1", "#fd7e14", "#20c997", "#6610f2"};
            for (String component : allComponents) {
                String borderColor = colors[colorIndex % colors.length];
                String backgroundColor = borderColor + "33";
                datasets.add(createDataset(component, componentSeries.get(component), borderColor, backgroundColor, true));
                colorIndex++;
            }

            chartData.put("months", months);
            chartData.put("datasets", datasets);
            chartData.put("year", year != null && !year.isEmpty() ? year : "");

            logger.info("Chart data: months={}, datasets={}, year={}", months, datasets, year);
            return chartData;
        } catch (Exception e) {
            logger.error("Error preparing chart data for year {}: {}", year, e.getMessage(), e);
            throw new Exception("Error preparing chart data: " + e.getMessage());
        }
    }

    private Map<String, Object> createDataset(String label, List<Double> data, String borderColor, String backgroundColor, boolean hidden) {
        Map<String, Object> dataset = new HashMap<>();
        dataset.put("label", label);
        dataset.put("data", data);
        dataset.put("borderColor", borderColor);
        dataset.put("backgroundColor", backgroundColor);
        dataset.put("fill", false);
        dataset.put("tension", 0.1);
        dataset.put("hidden", hidden);
        logger.info("Created dataset: label={}, data={}", label, data);
        return dataset;
    }

    // Convert a List<Salary> to List<SalarySlip>
    public List<SalarySlip> convertSalariesToSalarySlips(List<Salary> salaries, String sid) {
        logger.info("Converting {} Salary objects to SalarySlip objects, instance: {}", 
                    salaries != null ? salaries.size() : 0, this.hashCode());
        List<SalarySlip> salarySlips = new ArrayList<>();

        if (salaries == null || salaries.isEmpty()) {
            logger.warn("Input list of Salaries is null or empty, instance: {}", this.hashCode());
            return salarySlips;
        }

        SimpleDateFormat monthFormat = new SimpleDateFormat("MMMM", Locale.ENGLISH);
        String[] months = monthFormat.getDateFormatSymbols().getMonths();

        for (Salary salary : salaries) {
            try {
                SalarySlip slip = new SalarySlip();
                slip.setUtilService(utilService); 
                slip.setName(salary.getName());
                slip.setEmployeeId(salary.getEmployee());

                // Convert month name and year to a formatted date string (e.g., "01/MM/yyyy")
                String monthStr = salary.getMonth();
                Integer year = salary.getYear();
                String formattedMonth = null;
                if (monthStr != null && year != null) {
                    int monthIndex = Arrays.asList(months).indexOf(monthStr);
                    if (monthIndex >= 0) {
                        // Create a date string like "01/MM/yyyy"
                        formattedMonth = String.format("01/%02d/%d", monthIndex + 1, year);
                        // Optionally format to match utilService expectations
                        formattedMonth = utilService.formatDate(utilService.getFormattedDate(formattedMonth), "dd/MM/yyyy");
                    }
                } else if (salary.getPostingDate() != null) {
                    // Fallback to postingDate if month or year is null
                    formattedMonth = utilService.formatDate(salary.getPostingDate(), "dd/MM/yyyy");
                }

                if (formattedMonth == null) {
                    logger.warn("Could not determine valid month for Salary {}, instance: {}", salary.getName(), this.hashCode());
                    continue; // Skip if no valid month can be determined
                }
                slip.setMonth(formattedMonth);

                // Set baseSalary from earnings where description is "Salaire Base"
                Double base = 0.0;
                List<Component> earnings = salary.getEarnings();
                if (earnings != null) {
                    for (Component earning : earnings) {
                        if ("Salaire Base".equalsIgnoreCase(earning.getDescription())) {
                            base = earning.getAmount();
                            break;
                        }
                    }
                }
                slip.setBaseSalary(base);

                // Set salaryStructure (as per your previous requirement)
                String salaryStructure = null;
                if (earnings != null) {
                    for (Component earning : earnings) {
                        if ("Salaire Base".equalsIgnoreCase(earning.getDescription())) {
                            salaryStructure = earning.getAmount().toString();
                            break;
                        }
                    }
                }

                List<SalaryStructureAssignment> structure= validationService.searchSalaryStructureAssignments(null, sid);
                slip.setSalaryStructure(structure.get(0).getSalaryStructure());

                salarySlips.add(slip);
            } catch (Exception e) {
                logger.error("Error converting Salary {} to SalarySlip, instance: {}: {}", 
                            salary.getName(), this.hashCode(), e.getMessage());
                continue; // Skip invalid entries
            }
        }

        logger.info("Converted {} Salary objects to {} SalarySlip objects, instance: {}", 
                    salaries.size(), salarySlips.size(), this.hashCode());
        return salarySlips;
    }
}