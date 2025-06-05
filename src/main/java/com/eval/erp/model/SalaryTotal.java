package com.eval.erp.model;

import java.util.Map;

public class SalaryTotal {
    private String month;
    private Integer year;
    private Double totalGrossPay;
    private Double totalDeductions;
    private Double totalNetPay;
    private Map<String, Double> earningsDetails; // Maps component name to total amount
    private Map<String, Double> deductionsDetails; // Maps component name to total amount

    // Constructor
    public SalaryTotal(String month, Integer year, Double totalGrossPay, Double totalDeductions,
                       Double totalNetPay, Map<String, Double> earningsDetails, 
                       Map<String, Double> deductionsDetails) {
        this.month = month;
        this.year = year;
        this.totalGrossPay = totalGrossPay;
        this.totalDeductions = totalDeductions;
        this.totalNetPay = totalNetPay;
        this.earningsDetails = earningsDetails;
        this.deductionsDetails = deductionsDetails;
    }

    // Getters and Setters
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
    public Map<String, Double> getEarningsDetails() { return earningsDetails; }
    public void setEarningsDetails(Map<String, Double> earningsDetails) { this.earningsDetails = earningsDetails; }
    public Map<String, Double> getDeductionsDetails() { return deductionsDetails; }
    public void setDeductionsDetails(Map<String, Double> deductionsDetails) { this.deductionsDetails = deductionsDetails; }
}