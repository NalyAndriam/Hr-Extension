package com.eval.erp.model;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SalaryComponent {
    private static final Logger logger = LoggerFactory.getLogger(SalaryComponent.class);

    private String salaryStructure;
    private String name;
    private String salary_component_abbr; // Changement de "abbr" à "salary_component_abbr"
    private String type;
    private String valeur;
    private String company;

    public SalaryComponent() {
    }

    public void validate() {
        if (salaryStructure == null || salaryStructure.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom de la structure salariale est requis.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Le nom du composant est requis.");
        }
        if (salary_component_abbr == null || salary_component_abbr.trim().isEmpty()) { // Changement ici
            throw new IllegalArgumentException("L'abréviation du composant est requise.");
        }
        if (type == null || (!type.equalsIgnoreCase("earning") && !type.equalsIgnoreCase("deduction"))) {
            throw new IllegalArgumentException("Le type doit être 'earning' ou 'deduction'.");
        }
        if (valeur == null || valeur.trim().isEmpty()) {
            throw new IllegalArgumentException("La valeur ou formule est requise.");
        }
        if (company == null || company.trim().isEmpty()) {
            throw new IllegalArgumentException("La société est requise.");
        }
    }

    // Getters et Setters
    public String getSalaryStructure() {
        return salaryStructure;
    }

    public void setSalaryStructure(String salaryStructure) {
        this.salaryStructure = salaryStructure;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSalaryComponentAbbr() { // Changement ici
        return salary_component_abbr;
    }

    public void setSalaryComponentAbbr(String salary_component_abbr) { // Changement ici
        this.salary_component_abbr = salary_component_abbr;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValeur() {
        return valeur;
    }

    public void setValeur(String valeur) {
        this.valeur = valeur;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    private String normalizeName(String name) {
        if (name == null) return null;
        return name.replaceAll("\\s+", "-")
                   .replaceAll("[éèêë]", "e")
                   .replaceAll("[àáâãäå]", "a")
                   .replaceAll("[îï]", "i")
                   .replaceAll("[ôö]", "o")
                   .replaceAll("[ùúûü]", "u");
    }

    public Map<String, Object> toMap(boolean isUpdate) {
        Map<String, Object> map = new HashMap<>();
        if (!isUpdate) {
            map.put("doctype", "Salary Component");
        }
        map.put("salary_component", name);
        if (isUpdate) {
            map.put("name", normalizeName(name));
        }
        map.put("salary_component_abbr", salary_component_abbr); // Changement ici
        map.put("type", type.equalsIgnoreCase("earning") ? "Earning" : "Deduction");
        map.put("company", company);
        if (!valeur.equalsIgnoreCase("base")) {
            map.put("formula", valeur);
            map.put("amount_based_on_formula", 1);
        }
        map.put("is_payable", 1);
        map.put("depends_on_payment_days", type.equalsIgnoreCase("earning") && valeur.equalsIgnoreCase("base") ? 1 : 0);
        map.put("is_tax_applicable", type.equalsIgnoreCase("earning") ? 1 : 0);
        logger.info("Données envoyées pour Salary Component '{}': {}", name, map);
        return map;
    }
}