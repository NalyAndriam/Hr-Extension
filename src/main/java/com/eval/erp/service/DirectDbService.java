package com.eval.erp.service;

import com.eval.erp.model.Employee;
import com.eval.erp.model.SalaryComponent;
import com.eval.erp.model.SalaryStructure;
import com.eval.erp.model.SalarySlip;
import com.eval.erp.model.SalaryStructureAssignment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DirectDbService {

    private static final Logger logger = LoggerFactory.getLogger(DirectDbService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UtilService utilService;

    // ------------------------------------ Employee ------------------------------------

    /**
     * Récupère un employé par son ID.
     */
    public Employee getEmployeeById(String employeeId) {
        try {
            String sql = "SELECT name, employee_name, first_name, last_name, gender, date_of_birth, date_of_joining, " +
                         "company, department, designation, status " +
                         "FROM `tabEmployee` WHERE name = ?";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, employeeId);
            if (result == null || result.isEmpty()) {
                logger.warn("Employee with ID {} not found", employeeId);
                return null;
            }
            Employee employee = new Employee();
            employee.setUtilService(utilService);
            employee.setName((String) result.get("name"));
            employee.setEmployeeName((String) result.get("employee_name"));
            employee.setFirstName((String) result.get("first_name"));
            employee.setLastName((String) result.get("last_name"));
            employee.setGender((String) result.get("gender"));
            employee.setDateNaissance((String) result.get("date_of_birth"));
            employee.setDateEmbauche((String) result.get("date_of_joining"));
            employee.setCompany((String) result.get("company"));
            employee.setDepartment((String) result.get("department"));
            employee.setDesignation((String) result.get("designation"));
            employee.setStatus((String) result.get("status"));
            logger.info("Retrieved employee with ID {}", employeeId);
            return employee;
        } catch (Exception e) {
            logger.error("Error retrieving employee with ID {}: {}", employeeId, e.getMessage());
            throw new RuntimeException("Failed to retrieve employee: " + e.getMessage(), e);
        }
    }

    /**
     * Liste les employés d'une société.
     */
    public List<Employee> listEmployeesByCompany(String company) {
        try {
            String sql = "SELECT name, employee_name, first_name, last_name, gender, date_of_birth, date_of_joining, " +
                         "company, department, designation, status " +
                         "FROM `tabEmployee` WHERE company = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, company);
            List<Employee> employees = new ArrayList<>();
            for (Map<String, Object> result : results) {
                Employee employee = new Employee();
                employee.setUtilService(utilService);
                employee.setName((String) result.get("name"));
                employee.setEmployeeName((String) result.get("employee_name"));
                employee.setFirstName((String) result.get("first_name"));
                employee.setLastName((String) result.get("last_name"));
                employee.setGender((String) result.get("gender"));
                employee.setDateNaissance((String) result.get("date_of_birth"));
                employee.setDateEmbauche((String) result.get("date_of_joining"));
                employee.setCompany((String) result.get("company"));
                employee.setDepartment((String) result.get("department"));
                employee.setDesignation((String) result.get("designation"));
                employee.setStatus((String) result.get("status"));
                employees.add(employee);
            }
            logger.info("Retrieved {} employees for company {}", employees.size(), company);
            return employees;
        } catch (Exception e) {
            logger.error("Error listing employees for company {}: {}", company, e.getMessage());
            throw new RuntimeException("Failed to list employees: " + e.getMessage(), e);
        }
    }

    /**
     * Insère un nouvel employé dans la base de données.
     * @param employee L'objet Employee contenant les données à insérer.
     * @return true si l'insertion réussit, false sinon.
     */
    public boolean insertEmployee(Employee employee) {
        try {
            // Valider les données de l'employé
            employee.setUtilService(utilService);
            employee.validate(); // Assure que les champs requis sont présents

            // Générer un ID unique pour l'employé (simplifié pour l'exemple)
            String employeeId = generateEmployeeId(employee.getCompany());
            employee.setName(employeeId);

            // Préparer la requête SQL d'insertion
            String sql = "INSERT INTO `tabEmployee` (" +
                         "name, employee_name, first_name, last_name, gender, date_of_birth, date_of_joining, " +
                         "company, department, designation, status, creation, modified, modified_by, owner) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), ?, ?)";

            // Mapper les valeurs aux paramètres de la requête
            int rowsAffected = jdbcTemplate.update(sql,
                employee.getName(),
                employee.getEmployeeName(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.getGender(),
                employee.getDateNaissance(),
                employee.getDateEmbauche(),
                employee.getCompany(),
                employee.getDepartment(),
                employee.getDesignation(),
                employee.getStatus() != null ? employee.getStatus() : "Active",
                "Administrator", // modified_by (simplifié, utiliser l'utilisateur connecté en production)
                "Administrator"  // owner (simplifié, utiliser l'utilisateur connecté en production)
            );

            if (rowsAffected == 1) {
                logger.info("Successfully inserted employee with ID {}", employeeId);
                return true;
            } else {
                logger.error("Failed to insert employee with ID {}", employeeId);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error inserting employee with ID {}: {}", employee.getName(), e.getMessage());
            throw new RuntimeException("Failed to insert employee: " + e.getMessage(), e);
        }
    }

    /**
     * Génère un ID unique pour un employé basé sur la société.
     * (Simplifié pour l'exemple, en production utiliser la logique de numérotation d'ERPNext)
     */
    private String generateEmployeeId(String company) {
        try {
            // Compter le nombre d'employés existants pour générer un numéro séquentiel
            String sql = "SELECT COUNT(*) FROM `tabEmployee` WHERE company = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, company);
            String sequence = String.format("%05d", (count != null ? count + 1 : 1));
            return "EMP-" + company.substring(0, 3).toUpperCase() + "-" + sequence; // Exemple: EMP-ABC-00001
        } catch (Exception e) {
            logger.error("Error generating employee ID for company {}: {}", company, e.getMessage());
            throw new RuntimeException("Failed to generate employee ID: " + e.getMessage(), e);
        }
    }

    // ------------------------------------ SalaryComponent ------------------------------------

    /**
     * Récupère un composant salarial par son nom.
     */
    public SalaryComponent getSalaryComponentByName(String componentName) {
        try {
            String sql = "SELECT name, salary_component, salary_component_abbr, type, formula, company " +
                         "FROM `tabSalary Component` WHERE salary_component = ?";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, componentName);
            if (result == null || result.isEmpty()) {
                logger.warn("Salary Component {} not found", componentName);
                return null;
            }
            SalaryComponent component = new SalaryComponent();
            component.setName((String) result.get("salary_component"));
            component.setSalaryComponentAbbr((String) result.get("salary_component_abbr"));
            component.setType((String) result.get("type"));
            component.setValeur((String) result.get("formula"));
            component.setCompany((String) result.get("company"));
            logger.info("Retrieved salary component {}", componentName);
            return component;
        } catch (Exception e) {
            logger.error("Error retrieving salary component {}: {}", componentName, e.getMessage());
            throw new RuntimeException("Failed to retrieve salary component: " + e.getMessage(), e);
        }
    }

    /**
     * Liste les composants salariaux d'une société.
     */
    public List<SalaryComponent> listSalaryComponentsByCompany(String company) {
        try {
            String sql = "SELECT name, salary_component, salary_component_abbr, type, formula, company " +
                         "FROM `tabSalary Component` WHERE company = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, company);
            List<SalaryComponent> components = new ArrayList<>();
            for (Map<String, Object> result : results) {
                SalaryComponent component = new SalaryComponent();
                component.setName((String) result.get("salary_component"));
                component.setSalaryComponentAbbr((String) result.get("salary_component_abbr"));
                component.setType((String) result.get("type"));
                component.setValeur((String) result.get("formula"));
                component.setCompany((String) result.get("company"));
                components.add(component);
            }
            logger.info("Retrieved {} salary components for company {}", components.size(), company);
            return components;
        } catch (Exception e) {
            logger.error("Error listing salary components for company {}: {}", company, e.getMessage());
            throw new RuntimeException("Failed to list salary components: " + e.getMessage(), e);
        }
    }

    // ------------------------------------ SalaryStructure ------------------------------------

    /**
     * Récupère une structure salariale par son nom.
     */
    public SalaryStructure getSalaryStructureByName(String structureName) {
        try {
            // Récupérer les informations principales
            String sql = "SELECT name, company, payroll_frequency, is_active " +
                         "FROM `tabSalary Structure` WHERE name = ?";
            Map<String, Object> structureResult = jdbcTemplate.queryForMap(sql, structureName);
            if (structureResult == null || structureResult.isEmpty()) {
                logger.warn("Salary Structure {} not found", structureName);
                return null;
            }
            SalaryStructure structure = new SalaryStructure();
            structure.setSalaryStructureName((String) structureResult.get("name"));
            structure.setCompany((String) structureResult.get("company"));

            // Récupérer les composants (earnings et deductions)
            String componentSql = "SELECT salary_component, salary_component_abbr, amount_based_on_formula, formula, type " +
                                 "FROM `tabSalary Detail` WHERE parent = ? AND parenttype = 'Salary Structure'";
            List<Map<String, Object>> componentResults = jdbcTemplate.queryForList(componentSql, structureName);
            List<SalaryComponent> components = new ArrayList<>();
            for (Map<String, Object> componentResult : componentResults) {
                SalaryComponent component = new SalaryComponent();
                component.setName((String) componentResult.get("salary_component"));
                component.setSalaryComponentAbbr((String) componentResult.get("salary_component_abbr"));
                component.setType((String) componentResult.get("type"));
                component.setValeur((String) componentResult.get("formula"));
                component.setCompany(structure.getCompany());
                components.add(component);
            }
            structure.setComponents(components);
            logger.info("Retrieved salary structure {}", structureName);
            return structure;
        } catch (Exception e) {
            logger.error("Error retrieving salary structure {}: {}", structureName, e.getMessage());
            throw new RuntimeException("Failed to retrieve salary structure: " + e.getMessage(), e);
        }
    }

    /**
     * Liste les structures salariales d'une société.
     */
    public List<SalaryStructure> listSalaryStructuresByCompany(String company) {
        try {
            String sql = "SELECT name, company, payroll_frequency, is_active " +
                         "FROM `tabSalary Structure` WHERE company = ?";
            List<Map<String, Object>> structureResults = jdbcTemplate.queryForList(sql, company);
            List<SalaryStructure> structures = new ArrayList<>();
            for (Map<String, Object> structureResult : structureResults) {
                SalaryStructure structure = new SalaryStructure();
                structure.setSalaryStructureName((String) structureResult.get("name"));
                structure.setCompany((String) structureResult.get("company"));

                // Récupérer les composants
                String componentSql = "SELECT salary_component, salary_component_abbr, amount_based_on_formula, formula, type " +
                                     "FROM `tabSalary Detail` WHERE parent = ? AND parenttype = 'Salary Structure'";
                List<Map<String, Object>> componentResults = jdbcTemplate.queryForList(componentSql, structure.getSalaryStructureName());
                List<SalaryComponent> components = new ArrayList<>();
                for (Map<String, Object> componentResult : componentResults) {
                    SalaryComponent component = new SalaryComponent();
                    component.setName((String) componentResult.get("salary_component"));
                    component.setSalaryComponentAbbr((String) componentResult.get("salary_component_abbr"));
                    component.setType((String) componentResult.get("type"));
                    component.setValeur((String) componentResult.get("formula"));
                    component.setCompany(structure.getCompany());
                    components.add(component);
                }
                structure.setComponents(components);
                structures.add(structure);
            }
            logger.info("Retrieved {} salary structures for company {}", structures.size(), company);
            return structures;
        } catch (Exception e) {
            logger.error("Error listing salary structures for company {}: {}", company, e.getMessage());
            throw new RuntimeException("Failed to list salary structures: " + e.getMessage(), e);
        }
    }

    /**
     * Suppression d'une salary structure
     */
    public boolean deleteSalaryStructure(String structureName) {
        try {
            String statusSql = "SELECT docstatus FROM `tabSalary Structure` WHERE name = ?";
            Integer docstatus = jdbcTemplate.queryForObject(statusSql, Integer.class, structureName);
            if (docstatus == null) {
                logger.warn("Salary Structure {} not found", structureName);
                return false;
            }
            if (docstatus == 1) {
                jdbcTemplate.update("UPDATE `tabSalary Structure` SET docstatus = 2 WHERE name = ?", structureName);
                logger.info("Cancelled salary structure {}", structureName);
            }
            jdbcTemplate.update("DELETE FROM `tabSalary Detail` WHERE parent = ? AND parenttype = 'Salary Structure'", structureName);
            jdbcTemplate.update("DELETE FROM `tabSalary Structure` WHERE name = ?", structureName);
            logger.info("Deleted salary structure {}", structureName);
            return true;
        } catch (Exception e) {
            logger.error("Error deleting salary structure {}: {}", structureName, e.getMessage());
            throw new RuntimeException("Failed to delete salary structure: " + e.getMessage(), e);
        }
    }

    // ------------------------------------ SalarySlip ------------------------------------

    /**
     * Récupère un bulletin de salaire par son ID.
     */
    public SalarySlip getSalarySlipById(String slipId) {
        try {
            String sql = "SELECT name, employee, start_date, end_date, gross_pay, salary_structure, docstatus " +
                         "FROM `tabSalary Slip` WHERE name = ?";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, slipId);
            if (result == null || result.isEmpty()) {
                logger.warn("Salary Slip with ID {} not found", slipId);
                return null;
            }
            SalarySlip slip = new SalarySlip();
            slip.setUtilService(utilService);
            slip.setEmployeeId((String) result.get("employee"));
            slip.setMonth((String) result.get("start_date"));
            slip.setBaseSalary(result.get("gross_pay") != null ? ((Number) result.get("gross_pay")).doubleValue() : 0.0);
            slip.setSalaryStructure((String) result.get("salary_structure"));
            slip.setStatus(result.get("docstatus") != null ? ((Number) result.get("docstatus")).intValue() : 0);
            logger.info("Retrieved salary slip {}", slipId);
            return slip;
        } catch (Exception e) {
            logger.error("Error retrieving salary slip {}: {}", slipId, e.getMessage());
            throw new RuntimeException("Failed to retrieve salary slip: " + e.getMessage(), e);
        }
    }

    /**
     * Liste les bulletins de salaire d'un employé.
     */
    public List<SalarySlip> listSalarySlipsByEmployee(String employeeId) {
        try {
            String sql = "SELECT name, employee, start_date, end_date, gross_pay, salary_structure, docstatus " +
                         "FROM `tabSalary Slip` WHERE employee = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, employeeId);
            List<SalarySlip> slips = new ArrayList<>();
            for (Map<String, Object> result : results) {
                SalarySlip slip = new SalarySlip();
                slip.setUtilService(utilService);
                slip.setEmployeeId((String) result.get("employee"));
                slip.setMonth((String) result.get("start_date"));
                slip.setBaseSalary(result.get("gross_pay") != null ? ((Number) result.get("gross_pay")).doubleValue() : 0.0);
                slip.setSalaryStructure((String) result.get("salary_structure"));
                slip.setStatus(result.get("docstatus") != null ? ((Number) result.get("docstatus")).intValue() : 0);
                slips.add(slip);
            }
            logger.info("Retrieved {} salary slips for employee {}", slips.size(), employeeId);
            return slips;
        } catch (Exception e) {
            logger.error("Error listing salary slips for employee {}: {}", employeeId, e.getMessage());
            throw new RuntimeException("Failed to list salary slips: " + e.getMessage(), e);
        }
    }

    // ------------------------------------ SalaryStructureAssignment ------------------------------------

    /**
     * Récupère une affectation de structure salariale par son ID.
     */
    public SalaryStructureAssignment getSalaryStructureAssignmentById(String assignmentId) {
        try {
            String sql = "SELECT name, employee, salary_structure, from_date, to_date, base, company " +
                         "FROM `tabSalary Structure Assignment` WHERE name = ?";
            Map<String, Object> result = jdbcTemplate.queryForMap(sql, assignmentId);
            if (result == null || result.isEmpty()) {
                logger.warn("Salary Structure Assignment with ID {} not found", assignmentId);
                return null;
            }
            SalaryStructureAssignment assignment = new SalaryStructureAssignment();
            assignment.setUtilService(utilService);
            assignment.setEmployeeId((String) result.get("employee"));
            assignment.setSalaryStructure((String) result.get("salary_structure"));
            assignment.setFromDate((String) result.get("from_date"));
            assignment.setToDate((String) result.get("to_date"));
            assignment.setBaseSalary(result.get("base") != null ? ((Number) result.get("base")).doubleValue() : 0.0);
            assignment.setCompany((String) result.get("company"));
            logger.info("Retrieved salary structure assignment {}", assignmentId);
            return assignment;
        } catch (Exception e) {
            logger.error("Error retrieving salary structure assignment {}: {}", assignmentId, e.getMessage());
            throw new RuntimeException("Failed to retrieve salary structure assignment: " + e.getMessage(), e);
        }
    }

    /**
     * Liste les affectations de structure salariale d'un employé.
     */
    public List<SalaryStructureAssignment> listSalaryStructureAssignmentsByEmployee(String employeeId) {
        try {
            String sql = "SELECT name, employee, salary_structure, from_date, to_date, base, company " +
                         "FROM `tabSalary Structure Assignment` WHERE employee = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, employeeId);
            List<SalaryStructureAssignment> assignments = new ArrayList<>();
            for (Map<String, Object> result : results) {
                SalaryStructureAssignment assignment = new SalaryStructureAssignment();
                assignment.setUtilService(utilService);
                assignment.setEmployeeId((String) result.get("employee"));
                assignment.setSalaryStructure((String) result.get("salary_structure"));
                assignment.setFromDate((String) result.get("from_date"));
                assignment.setToDate((String) result.get("to_date"));
                assignment.setBaseSalary(result.get("base") != null ? ((Number) result.get("base")).doubleValue() : 0.0);
                assignment.setCompany((String) result.get("company"));
                assignments.add(assignment);
            }
            logger.info("Retrieved {} salary structure assignments for employee {}", assignments.size(), employeeId);
            return assignments;
        } catch (Exception e) {
            logger.error("Error listing salary structure assignments for employee {}: {}", employeeId, e.getMessage());
            throw new RuntimeException("Failed to list salary structure assignments: " + e.getMessage(), e);
        }
    }
}