package com.eval.erp.model;

public class Component {
    private String description;
    private Double amount;

    // Constructeur par défaut
    public Component() {}

    // Constructeur avec paramètres
    public Component(String description, Double amount) {
        this.description = description;
        this.amount = amount;
    }

    // Getters et setters
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }
}
