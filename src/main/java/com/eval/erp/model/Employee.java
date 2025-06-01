package com.eval.erp.model;

public class Employee {
    private String name;
    private String employeeName;
    private String department;
    private String designation;
    private String status;

    public Employee() {}

    public Employee(String name, String employeeName, String department, String designation, String status) {
        this.name = name;
        this.employeeName = employeeName;
        this.department = department;
        this.designation = designation;
        this.status = status;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}