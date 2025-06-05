package com.eval.erp.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalaryTotal {
    private String month;
    private Integer year;
    private Double totalGrossPay;
    private Double totalDeductions;
    private Double totalNetPay;
    private List<Salary> salaries;
    private Map<String, Double> componentTotals;
    private Map<String, Double> earningsDetails; // New field for earnings
    private Map<String, Double> deductionsDetails; // New field for deductions

    public SalaryTotal(String month, Integer year, Double totalGrossPay, Double totalDeductions, 
                      Double totalNetPay, List<Salary> salaries, Map<String, Double> componentTotals) {
        this.month = month;
        this.year = year;
        this.totalGrossPay = totalGrossPay;
        this.totalDeductions = totalDeductions;
        this.totalNetPay = totalNetPay;
        this.salaries = salaries;
        this.componentTotals = componentTotals;
        
        // Initialize earningsDetails and deductionsDetails
        this.earningsDetails = new HashMap<>();
        this.deductionsDetails = new HashMap<>();
        
        // Split componentTotals into earnings and deductions
        if (componentTotals != null) {
            for (Map.Entry<String, Double> entry : componentTotals.entrySet()) {
                if (entry.getKey().startsWith("Earning: ")) {
                    this.earningsDetails.put(entry.getKey().substring(9), entry.getValue());
                } else if (entry.getKey().startsWith("Deduction: ")) {
                    this.deductionsDetails.put(entry.getKey().substring(11), entry.getValue());
                }
            }
        }
    }

    // Getters and setters
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Double getTotalGrossPay() { return totalGrossPay; }
    public void setTotalGrossPay(Double totalGrossPay) { this.totalGrossPay = totalGrossPay; }
    public Double getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(Double totalDeductions) { this.totalDeductions = totalDeductions; }
    public Double getTotalNetPay() { return totalNetPay; }
    public void setTotalNetPay(Double totalNetPay) { this.totalNetPay = totalNetPay; }
    public List<Salary> getSalaries() { return salaries; }
    public void setSalaries(List<Salary> salaries) { this.salaries = salaries; }
    public Map<String, Double> getComponentTotals() { return componentTotals; }
    public void setComponentTotals(Map<String, Double> componentTotals) { this.componentTotals = componentTotals; }
    public Map<String, Double> getEarningsDetails() { return earningsDetails; }
    public void setEarningsDetails(Map<String, Double> earningsDetails) { this.earningsDetails = earningsDetails; }
    public Map<String, Double> getDeductionsDetails() { return deductionsDetails; }
    public void setDeductionsDetails(Map<String, Double> deductionsDetails) { this.deductionsDetails = deductionsDetails; }
}