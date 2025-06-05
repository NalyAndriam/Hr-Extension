package com.eval.erp.model;

import java.util.Date;
import java.util.List;

public class Salary {
    private String name; // Salary Slip ID
    private String employee; // Employee ID
    private String employeeName; // Employee Name
    private String month; // Month of salary
    private Integer year; // Year of salary
    private Double grossPay; // Gross salary amount
    private Double netPay; // Net salary amount
    private Date startDate; // Start date of salary period
    private Date endDate; // End date of salary period
    private Date postingDate; // Date of salary posting
    private String status; // Status of the payslip
    private Double totalDeduction; // Total deductions
    private String payrollFrequency; // Payroll frequency (e.g., Monthly)
    private String totalInWords; // Total amount in words
    private String company; // Company name
    private String department; // Department
    private String designation; // Designation
    private Double totalWorkingDays; // Total working days
    private Double paymentDays; // Payment days
    private String currency; // Currency

    private List<Component> earnings;
    private List<Component> deductions;

    public List<Component> getEarnings() {
        return earnings;
    }

    public void setEarnings(List<Component> earnings) {
        this.earnings = earnings;
    }


    public List<Component> getDeductions() {
        return deductions;
    }

    public void setDeductions(List<Component> deductions) {
        this.deductions = deductions;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmployee() {
        return employee;
    }

    public void setEmployee(String employee) {
        this.employee = employee;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Double getGrossPay() {
        return grossPay;
    }

    public void setGrossPay(Double grossPay) {
        this.grossPay = grossPay;
    }

    public Double getNetPay() {
        return netPay;
    }

    public void setNetPay(Double netPay) {
        this.netPay = netPay;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public Date getPostingDate() {
        return postingDate;
    }

    public void setPostingDate(Date postingDate) {
        this.postingDate = postingDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Double getTotalDeduction() {
        return totalDeduction;
    }

    public void setTotalDeduction(Double totalDeduction) {
        this.totalDeduction = totalDeduction;
    }

    public String getPayrollFrequency() {
        return payrollFrequency;
    }

    public void setPayrollFrequency(String payrollFrequency) {
        this.payrollFrequency = payrollFrequency;
    }

    public String getTotalInWords() {
        return totalInWords;
    }

    public void setTotalInWords(String totalInWords) {
        this.totalInWords = totalInWords;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public Double getTotalWorkingDays() {
        return totalWorkingDays;
    }

    public void setTotalWorkingDays(Double totalWorkingDays) {
        this.totalWorkingDays = totalWorkingDays;
    }

    public Double getPaymentDays() {
        return paymentDays;
    }

    public void setPaymentDays(Double paymentDays) {
        this.paymentDays = paymentDays;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}