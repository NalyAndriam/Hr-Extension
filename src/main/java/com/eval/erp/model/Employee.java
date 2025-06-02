package com.eval.erp.model;

import com.eval.erp.service.UtilService;
import com.opencsv.bean.CsvBindByName;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Employee {

    private static final Logger logger = LoggerFactory.getLogger(Employee.class);

    private UtilService utilService;

    @CsvBindByName(column = "Ref")
    private String name;

    @CsvBindByName(column = "Nom")
    private String lastName;

    @CsvBindByName(column = "Prenom")
    private String firstName;

    @CsvBindByName(column = "genre")
    private String gender;

    @CsvBindByName(column = "Date embauche")
    private String dateEmbauche;

    @CsvBindByName(column = "date naissance")
    private String dateNaissance;

    @CsvBindByName(column = "company")
    private String company;

    private String employeeName;

    private Date dateOfJoining;
    private Date dateOfBirth;

    private String department;
    private String designation;
    private String status;

    private static final String API_DATE_FORMAT = "yyyy-MM-dd";

    public void validate() throws IllegalArgumentException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID (Ref) cannot be empty");
        }
        if (lastName == null || lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name cannot be empty");
        }
        if (firstName == null || firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name cannot be empty");
        }
        if (gender == null || gender.trim().isEmpty()) {
            throw new IllegalArgumentException("Gender cannot be empty");
        }
        if (dateEmbauche == null || dateEmbauche.trim().isEmpty()) {
            throw new IllegalArgumentException("Date embauche cannot be empty");
        }
        if (dateNaissance == null || dateNaissance.trim().isEmpty()) {
            throw new IllegalArgumentException("Date naissance cannot be empty");
        }
        if (company == null || company.trim().isEmpty()) {
            throw new IllegalArgumentException("Company cannot be empty");
        }
    }

    public void setName(String name) {
        logger.info("Setting name: {}", name);
        this.name = name != null ? name.trim() : null;
    }

    public void setLastName(String lastName) {
        logger.info("Setting lastName: {}", lastName);
        this.lastName = lastName != null ? lastName.trim() : null;
        updateEmployeeName();
    }

    public void setFirstName(String firstName) {
        logger.info("Setting firstName: {}", firstName);
        this.firstName = firstName != null ? firstName.trim() : null;
        updateEmployeeName();
    }

    private void updateEmployeeName() {
        if (firstName != null && lastName != null) {
            this.employeeName = firstName + " " + lastName;
        }
    }

    public void setGenre(String genre) throws IllegalArgumentException {
        logger.info("Setting genre: {}", genre);
        if (genre == null || genre.trim().isEmpty()) {
            throw new IllegalArgumentException("Genre cannot be empty");
        }
        if (genre.equalsIgnoreCase("Masculin")) {
            this.gender = "Male";
        } else if (genre.equalsIgnoreCase("Feminin")) {
            this.gender = "Female";
        } 
    }

    public void setGender(String gender) {
        logger.info("Setting gender: {}", gender);
        this.gender = gender != null ? gender.trim() : null;
    }

    public void setDateEmbauche(String dateEmbauche) throws IllegalArgumentException {
        logger.info("Setting dateEmbauche: {}", dateEmbauche);
        if (dateEmbauche == null || dateEmbauche.trim().isEmpty()) {
            throw new IllegalArgumentException("Date embauche cannot be empty");
        }
        this.dateEmbauche = dateEmbauche.trim();
        try {
            if (utilService == null) {
                throw new IllegalStateException("UtilService is not set");
            }
            this.dateOfJoining = utilService.getFormattedDate(dateEmbauche.trim());
            if (this.dateOfJoining == null) {
                throw new IllegalArgumentException("Invalid date format for Date embauche: " + dateEmbauche);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format for Date embauche: " + dateEmbauche + ". Expected formats: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd");
        }
    }


    public void setDateNaissance(String dateNaissance) throws IllegalArgumentException {
        logger.info("Setting dateNaissance: {}", dateNaissance);
        if (dateNaissance == null || dateNaissance.trim().isEmpty()) {
            throw new IllegalArgumentException("Date naissance cannot be empty");
        }
        this.dateNaissance = dateNaissance.trim();
        try {
            if (utilService == null) {
                throw new IllegalStateException("UtilService is not set");
            }
            this.dateOfBirth = utilService.getFormattedDate(dateNaissance.trim());
            if (this.dateOfBirth == null) {
                throw new IllegalArgumentException("Invalid date format for date naissance: " + dateNaissance);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format for date naissance: " + dateNaissance + ". Expected formats: dd/MM/yyyy, dd-MM-yyyy, yyyy-MM-dd");
        }
    }

    public void setCompany(String company) {
        logger.info("Setting company: {}", company);
        this.company = company != null ? company.trim() : null;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setDateOfJoining (Date date){
        this.dateOfJoining= date;
    }

    public void setDateOfBirth (Date date){
        this.dateOfBirth= date;
    }

    public String getName() {
        return name;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getGender() {
        return gender;
    }

    public Date getDateOfJoining() {
        return dateOfJoining;
    }

    public Date getDateOfBirth() {
        return dateOfBirth;
    }

    public String getCompany() {
        return company;
    }

    public String getDepartment() {
        return department;
    }

    public String getDesignation() {
        return designation;
    }

    public String getStatus() {
        return status;
    }

    public String getDateEmbauche() {
        return dateEmbauche;
    }

    public String getDateNaissance() {
        return dateNaissance;
    }

    public Map<String, Object> toMap(boolean isUpdate) {
        Map<String, Object> map = new HashMap<>();
        if (isUpdate) {
            map.put("name", name);
        }
        map.put("employee_name", employeeName);
        map.put("first_name", firstName);
        map.put("last_name", lastName);
        map.put("gender", gender);
        map.put("date_of_joining", utilService.formatDate(dateOfJoining, API_DATE_FORMAT));
        map.put("date_of_birth", utilService.formatDate(dateOfBirth, API_DATE_FORMAT));
        map.put("company", company);
        return map;
    }

    public void setUtilService(UtilService utilService) {
        logger.info("Setting utilService");
        this.utilService = utilService;
    }

}