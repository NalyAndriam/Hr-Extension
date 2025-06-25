package com.eval.erp.service;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UtilService {

    private static final Logger logger = LoggerFactory.getLogger(UtilService.class);

    private static final List<String> DATE_FORMATS = Arrays.asList(
        "dd-MM-yyyy",
        "dd/MM/yyyy",
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "MM-dd-yyyy",
        "MM/dd/yyyy",
        "dd MMM yyyy",
        "dd MMMM yyyy",
        "yyyyMMdd",
        "ddMMyyyy"
    );

    public Date getFormattedDate(String dateStr) throws IllegalArgumentException {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            logger.error("Date string is null or empty");
            throw new IllegalArgumentException("Date string cannot be null or empty");
        }

        String trimmedDate = dateStr.trim();
        for (String pattern : DATE_FORMATS) {
            try {
                SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
                dateFormat.setLenient(false); // Strict parsing
                Date parsedDate = dateFormat.parse(trimmedDate);
                logger.debug("Successfully parsed date '{}' with pattern '{}'", trimmedDate, pattern);
                return parsedDate;
            } catch (Exception e) {
                logger.debug("Failed to parse date '{}' with pattern '{}': {}", trimmedDate, pattern, e.getMessage());
            }
        }

        logger.error("Unable to parse date '{}' with any supported pattern", trimmedDate);
        throw new IllegalArgumentException("Invalid date format: " + trimmedDate + ". Supported formats: " + DATE_FORMATS);
    }

    public String formatDate(Date date, String outputPattern) throws IllegalArgumentException {
        if (date == null) {
            logger.error("Date object is null");
            throw new IllegalArgumentException("Date cannot be null");
        }
        if (outputPattern == null || outputPattern.trim().isEmpty()) {
            logger.error("Output pattern is null or empty");
            throw new IllegalArgumentException("Output pattern cannot be null or empty");
        }

        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(outputPattern);
            String formattedDate = dateFormat.format(date);
            logger.debug("Formatted date '{}' to pattern '{}': {}", date, outputPattern, formattedDate);
            return formattedDate;
        } catch (Exception e) {
            logger.error("Error formatting date '{}' with pattern '{}': {}", date, outputPattern, e.getMessage());
            throw new IllegalArgumentException("Invalid output pattern: " + outputPattern);
        }
    }

    public String normalizeName(String name) {
        if (name == null) return null;
        return name.replaceAll("\\s+", "-")
                .replaceAll("[éèêë]", "e")
                .replaceAll("[àáâãäå]", "a")
                .replaceAll("[îï]", "i")
                .replaceAll("[ôö]", "o")
                .replaceAll("[ùúûü]", "u");
    }

    public boolean isNumeric(String str) {
        if (str == null || str.trim().isEmpty()) {
            logger.error("Input string is null or empty");
            return false;
        }
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            logger.error("Invalid numeric format: {}", str);
            return false;
        }
    }

    public String getEndOfMonth(String dateStr, String inputPattern) throws IllegalArgumentException {
        try {
            Date date = getFormattedDate(dateStr);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
            return formatDate(calendar.getTime(), "yyyy-MM-dd");
        } catch (Exception e) {
            logger.error("Error calculating end of month for date '{}': {}", dateStr, e.getMessage());
            throw new IllegalArgumentException("Error calculating end of month: " + e.getMessage());
        }
    }

    public String generateSalarySlipName(String employeeId, String month) {
        try {
            Date date = getFormattedDate(month);
            String formattedMonth = formatDate(date, "yyyy-MM");
            return String.format("Sal Slip/None/%s-%s", normalizeName(employeeId), formattedMonth); // Adjust if ERPNext format differs
        } catch (Exception e) {
            logger.error("Error generating Salary Slip name for employee {} and month {}: {}", employeeId, month, e.getMessage());
            throw new IllegalArgumentException("Error generating Salary Slip name: " + e.getMessage());
        }
    }

    public String convertMonthYearToFullDate(String input) {
        // Formateur pour "MM-yyyy"
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        // Formateur pour "dd-MM-yyyy"
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        // Parse en YearMonth
        YearMonth yearMonth = YearMonth.parse(input, inputFormatter);
        // Création d'une date avec le jour = 1
        LocalDate date = yearMonth.atDay(1);

        // Retour sous format souhaité
        return date.format(outputFormatter);
    }

    public double getPourcentage(double montant, double pourcentage) {
        double valeurPourcent = montant * (pourcentage / 100.0);
        return montant + valeurPourcent;
        // switch (signe) {
        //     case "+":
        //         return montant + valeurPourcent;
        //     case "-":
        //         return montant - valeurPourcent;
        //     default:
        //         throw new IllegalArgumentException("Signe invalide : utilisez '+' ou '-'");
        // }
    }
}