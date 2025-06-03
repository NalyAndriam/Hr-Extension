package com.eval.erp.controller;

import com.eval.erp.model.SalaryStructure;
import com.eval.erp.service.EmployeeService;
import com.eval.erp.service.ImportService;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

@Controller
public class ImportController {

    private static final Logger logger = LoggerFactory.getLogger(ImportController.class);

    @Autowired
    private ImportService importService;

    @Autowired
    private HttpSession session;

//---------------------------------------------------------EMPLOYEES-------------------------------------------------------------------
    @PostMapping("/employee/import")
    public String importEmployees(@RequestParam("csvFile") MultipartFile file, Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/import";
            }

            if (file.isEmpty()) {
                model.addAttribute("error", "Please upload a valid CSV file.");
                return "pages/import";
            }

            if (!file.getContentType().equals("text/csv") && !file.getContentType().equals("application/vnd.ms-excel")) {
                model.addAttribute("error", "Invalid file type. Please upload a CSV file.");
                return "pages/import";
            }

            List<String> importResults = importService.importEmployeesFromCsv(file, sid);
            model.addAttribute("importResults", importResults);

        } catch (Exception e) {
            logger.error("Error importing employees: {}", e.getMessage());
            model.addAttribute("error", e.getMessage());
        }

        return "pages/import";
    }

    @GetMapping("/employee/import")
    public String showImportPage(Model model) {
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                model.addAttribute("error", "ERPNext session not found. Please reconnect.");
                return "pages/import";
            }

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }

        return "pages/import";
    }

//-----------------------------------------------------SALARY STRUCTURE-------------------------------------------------------------------

    @GetMapping("/salary-structure/import")
    public String showImportForm(Model model) {
        logger.info("Affichage du formulaire d'importation de structure salariale");
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");
        return "pages/import";
    }

    @PostMapping("/salary-structure/import")
    public String importSalaryStructure(@RequestParam("csvFile") MultipartFile file, Model model) {
        logger.info("Importation du fichier CSV : {}", file.getOriginalFilename());
        model.addAttribute("username", SecurityContextHolder.getContext().getAuthentication().getName());
        model.addAttribute("activeMenu", "import");

        try {
            String sid = (String) session.getAttribute("erp_sid");
            if (sid == null) {
                logger.warn("Session ERPNext non trouvée pour l'importation");
                model.addAttribute("error", "Session ERPNext non trouvée. Veuillez vous reconnecter.");
                return "pages/import";
            }

            if (file.isEmpty()) {
                logger.warn("Fichier CSV vide");
                model.addAttribute("error", "Veuillez uploader un fichier CSV valide.");
                return "pages/import";
            }

            if (!file.getContentType().equals("text/csv") && !file.getContentType().equals("application/vnd.ms-excel")) {
                logger.warn("Type de fichier invalide : {}", file.getContentType());
                model.addAttribute("error", "Type de fichier invalide. Veuillez uploader un fichier CSV.");
                return "pages/import";
            }

            SalaryStructure summary = importService.importSalaryStructure(file, sid);
            model.addAttribute("importResults", summary.getResults());
            model.addAttribute("summary", summary);
            logger.info("Importation terminée : {} résultats", summary.getResults().size());
        } catch (Exception e) {
            logger.error("Erreur lors de l'importation du CSV : {}", e.getMessage());
            model.addAttribute("error", "Erreur lors de l'importation : " + e.getMessage());
        }

        return "pages/import";
    }
}