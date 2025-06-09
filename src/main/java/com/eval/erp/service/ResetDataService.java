package com.eval.erp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import jakarta.servlet.http.HttpSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ResetDataService {

    private static final Logger logger = LoggerFactory.getLogger(ResetDataService.class);

    @Autowired
    private ErpNextApiService erpNextApiService;

    @Autowired
    private HttpSession session;

    // Méthode pour tenter une suppression avec réessais manuels
    private ResponseEntity<Map> attemptDelete(String doctype, String recordName, String sid, List<String> results) {
        int maxAttempts = 3;
        int attempt = 1;
        while (attempt <= maxAttempts) {
            try {
                ResponseEntity<Map> response = erpNextApiService.deleteResource(doctype, recordName, sid);
                return response;
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                if (attempt == maxAttempts) {
                    results.add(String.format("Error deleting %s %s after %d attempts: %s", doctype, recordName, maxAttempts, errorMsg));
                    logger.error("Error deleting {} {} after {} attempts: {}", doctype, recordName, maxAttempts, errorMsg);
                    return null;
                }
                logger.warn("Attempt {}/{} failed for deleting {} {}: {}. Retrying...", attempt, maxAttempts, doctype, recordName, errorMsg);
                attempt++;
                try {
                    Thread.sleep(1000); // Délai de 1 seconde entre les tentatives
                } catch (InterruptedException ie) {
                    logger.error("Interrupted during retry delay: {}", ie.getMessage());
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                results.add(String.format("Unexpected error deleting %s %s: %s", doctype, recordName, e.getMessage()));
                logger.error("Unexpected error deleting {} {}: {}", doctype, recordName, e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    // Méthode pour tenter une annulation avec réessais manuels
    private ResponseEntity<Map> attemptCancel(String doctype, String recordName, String sid, List<String> results) {
        int maxAttempts = 3;
        int attempt = 1;
        while (attempt <= maxAttempts) {
            try {
                ResponseEntity<Map> response = erpNextApiService.cancelResource(doctype, recordName, sid);
                return response;
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                if (attempt == maxAttempts) {
                    results.add(String.format("Error canceling %s %s after %d attempts: %s", doctype, recordName, maxAttempts, errorMsg));
                    logger.error("Error canceling {} {} after {} attempts: {}", doctype, recordName, maxAttempts, errorMsg);
                    return null;
                }
                logger.warn("Attempt {}/{} failed for canceling {} {}: {}. Retrying...", attempt, maxAttempts, doctype, recordName, errorMsg);
                attempt++;
                try {
                    Thread.sleep(1000); // Délai de 1 seconde entre les tentatives
                } catch (InterruptedException ie) {
                    logger.error("Interrupted during retry delay: {}", ie.getMessage());
                    Thread.currentThread().interrupt();
                    return null;
                }
            } catch (Exception e) {
                results.add(String.format("Unexpected error canceling %s %s: %s", doctype, recordName, e.getMessage()));
                logger.error("Unexpected error canceling {} {}: {}", doctype, recordName, e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    public List<String> resetData(String sid) throws Exception {
        // Initialisation de la liste des résultats
        List<String> results = new ArrayList<>();

        // Nettoyage de la session
        session.removeAttribute("employeeRefToNameMap");
        logger.info("Cleared employeeRefToNameMap from session");

        logger.info("Starting data reset process");

        // Liste des doctypes avec dépendances
        String[] doctypes = {
            "Payroll Entry", // Ajouté pour gérer les dépendances
            "Salary Slip",
            "Salary Structure Assignment",
            "Salary Structure",
            "Employee",
            "Salary Component"
        };

        // Étape 1 : Suppression des enregistrements par lots
        for (String doctype : doctypes) {
            int pageSize = 100;
            int start = 0;
            boolean hasMore = true;

            while (hasMore) {
                try {
                    String fields = "[\"name\", \"docstatus\"]";
                    String filters = "";
                    // Récupération avec pagination
                    ResponseEntity<Map> response = erpNextApiService.getResource(doctype, fields, filters, sid, pageSize, start);
                    List<Map<String, Object>> records = (List<Map<String, Object>>) response.getBody().get("data");

                    if (records.isEmpty()) {
                        results.add(String.format("No records found in %s to delete successfully", doctype));
                        logger.info("No records found in {} to delete", doctype);
                        hasMore = false;
                        continue;
                    }

                    // Traitement des enregistrements
                    for (Map<String, Object> record : records) {
                        String recordName = (String) record.get("name");
                        Integer docstatus = (Integer) record.get("docstatus");
                        try {
                            // Gestion des documents selon leur statut
                            if (doctype.equals("Salary Slip") || doctype.equals("Salary Structure") || doctype.equals("Salary Structure Assignment")) {
                                if (docstatus == 0) {
                                    // Brouillon : suppression directe
                                    logger.info("{} {} is in Draft (docstatus: 0), deleting directly", doctype, recordName);
                                } else if (docstatus == 1) {
                                    // Soumis : annulation d'abord
                                    ResponseEntity<Map> cancelResponse = attemptCancel(doctype, recordName, sid, results);
                                    if (cancelResponse == null || !cancelResponse.getStatusCode().is2xxSuccessful()) {
                                        if (cancelResponse != null) {
                                            String errorMsg = cancelResponse.getBody() != null ? cancelResponse.getBody().toString() : "Unknown error";
                                            results.add(String.format("Failed to cancel %s %s: %s", doctype, recordName, errorMsg));
                                            logger.error("Failed to cancel {} {}: {}", doctype, recordName, errorMsg);
                                        }
                                        continue; // Passer à l'enregistrement suivant
                                    }
                                    results.add(String.format("%s %s successfully canceled", doctype, recordName));
                                    logger.info("{} {} successfully canceled", doctype, recordName);
                                } else if (docstatus == 2) {
                                    // Déjà annulé
                                    logger.info("{} {} is already cancelled (docstatus: 2)", doctype, recordName);
                                }
                            }

                            // Suppression de l'enregistrement
                            ResponseEntity<Map> deleteResponse = attemptDelete(doctype, recordName, sid, results);
                            if (deleteResponse != null && deleteResponse.getStatusCode().is2xxSuccessful()) {
                                results.add(String.format("%s %s successfully deleted", doctype, recordName));
                                logger.info("{} {} successfully deleted", doctype, recordName);
                            } else if (deleteResponse != null) {
                                String errorMsg = deleteResponse.getBody() != null ? deleteResponse.getBody().toString() : "Unknown error";
                                results.add(String.format("Failed to delete %s %s: %s", doctype, recordName, errorMsg));
                                logger.error("Failed to delete {} {}: {}", doctype, recordName, errorMsg);
                            }
                        } catch (Exception e) {
                            results.add(String.format("Error processing %s %s: %s", doctype, recordName, e.getMessage()));
                            logger.error("Error processing {} {}: {}", doctype, recordName, e.getMessage(), e);
                        }
                    }

                    start += pageSize;
                    hasMore = records.size() == pageSize;
                } catch (HttpClientErrorException e) {
                    String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                    if (e.getStatusCode().value() == 403) {
                        results.add(String.format("Permission denied for %s: %s", doctype, errorMsg));
                        logger.error("Permission denied for {}: {}", doctype, errorMsg);
                        break; // Arrêter pour ce doctype si permission refusée
                    }
                    results.add(String.format("Error fetching records for %s: %s", doctype, errorMsg));
                    logger.error("Error fetching records for {}: {}", doctype, errorMsg);
                    hasMore = false;
                } catch (Exception e) {
                    results.add(String.format("Error fetching records for %s: %s", doctype, e.getMessage()));
                    logger.error("Error fetching records for {}: {}", doctype, e.getMessage());
                    hasMore = false;
                }
            }
        }

        // Étape 2 : Vérification des enregistrements restants
        for (String doctype : doctypes) {
            try {
                ResponseEntity<Map> response = erpNextApiService.getResource(doctype, "[\"name\"]", "", sid);
                List<Map<String, Object>> remainingRecords = (List<Map<String, Object>>) response.getBody().get("data");
                if (!remainingRecords.isEmpty()) {
                    results.add(String.format("Warning: %d records remain in %s after deletion", remainingRecords.size(), doctype));
                    logger.warn("{} records remain in {} after deletion", remainingRecords.size(), doctype);
                }
            } catch (HttpClientErrorException e) {
                String errorMsg = e.getResponseBodyAsString().isEmpty() ? e.getStatusText() : e.getResponseBodyAsString();
                results.add(String.format("Error verifying remaining records for %s: %s", doctype, errorMsg));
                logger.error("Error verifying remaining records for {}: {}", doctype, errorMsg);
            } catch (Exception e) {
                results.add(String.format("Error verifying remaining records for %s: %s", doctype, e.getMessage()));
                logger.error("Error verifying remaining records for {}: {}", doctype, e.getMessage());
            }
        }

        logger.info("Data reset completed: {} results", results.size());
        return results;
    }
}