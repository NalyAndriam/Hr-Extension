package com.eval.erp.controller;

import com.eval.erp.service.ResetDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

@Controller
public class ResetDataController {

    private static final Logger logger = LoggerFactory.getLogger(ResetDataController.class);

    @Autowired
    private ResetDataService resetDataService;

    @Autowired
    private HttpSession session;

    @PostMapping("/reset-data")
    public String resetData(Model model) {
        // Ajout des attributs par défaut pour la vue
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");

        try {
            // Vérification de la session ERPNext
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                logger.warn("ERPNext session ID not found in session");
                model.addAttribute("importResults", new ArrayList<String>());
                return "pages/import";
            }

            logger.info("Initiating data reset for session ID: {}", sid);

            // Appel du service de réinitialisation
            List<String> importResults = resetDataService.resetData(sid);

            // Ajout des résultats au modèle pour la vue
            model.addAttribute("importResults", importResults != null ? importResults : new ArrayList<String>());

            // Message global pour l’utilisateur
            boolean hasErrors = importResults.stream().anyMatch(result -> result.contains("Error") || result.contains("Failed"));
            boolean hasWarnings = importResults.stream().anyMatch(result -> result.contains("Warning"));
            if (hasErrors) {
                model.addAttribute("error", "Data reset completed with errors. Check the results list for details.");
                logger.warn("Data reset completed with errors");
            } else if (hasWarnings) {
                model.addAttribute("error", "Data reset completed, but some records remain. Check the results list for details.");
                logger.warn("Data reset completed with warnings");
            } else {
                model.addAttribute("error", "Data reset completed successfully.");
                logger.info("Data reset completed successfully: {} results", importResults.size());
            }

        } catch (Exception e) {
            logger.error("Error during data reset: {}", e.getMessage(), e);
            model.addAttribute("error", "Error during data reset: " + e.getMessage());
            model.addAttribute("importResults", new ArrayList<String>());
        }

        return "pages/import";
    }
}