package com.eval.erp.model;

import java.util.List;

public class SalarySummary {

    private List<Salary> salaries;
        private double totalGrossPay;
        private double totalDeductions;
        private double totalNetPay;

        public SalarySummary(List<Salary> salaries, double totalGrossPay, double totalDeductions, double totalNetPay) {
            this.salaries = salaries;
            this.totalGrossPay = totalGrossPay;
            this.totalDeductions = totalDeductions;
            this.totalNetPay = totalNetPay;
        }

        public List<Salary> getSalaries() {
            return salaries;
        }

        public double getTotalGrossPay() {
            return totalGrossPay;
        }

        public double getTotalDeductions() {
            return totalDeductions;
        }

        public double getTotalNetPay() {
            return totalNetPay;
        }
    
}
