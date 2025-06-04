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
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");
        List<String> resetResults = new ArrayList<>();

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/import";
            }

            logger.info("Initiating data reset");
            resetResults = resetDataService.resetData(sid);
            model.addAttribute("importResults", resetResults);
            logger.info("Data reset completed: {} results", resetResults.size());

        } catch (Exception e) {
            logger.error("Error during data reset: {}", e.getMessage());
            model.addAttribute("error", "Error during data reset: " + e.getMessage());
        }

        return "pages/import";
    }
}