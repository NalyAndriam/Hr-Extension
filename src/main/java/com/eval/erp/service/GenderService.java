package com.eval.erp.service;

import com.eval.erp.model.Gender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GenderService {

    private static final Logger logger = LoggerFactory.getLogger(GenderService.class);

    private final ErpNextApiService erpNextApiService;

    public GenderService(ErpNextApiService erpNextApiService) {
        this.erpNextApiService = erpNextApiService;
    }

    private List<Gender> convertIntoGender(List<Map<String, Object>> genderData) {
        List<Gender> genders = new ArrayList<>();
        for (Map<String, Object> data : genderData) {
            Gender gender = new Gender();
            gender.setName((String) data.get("name"));
            genders.add(gender);
        }
        return genders;
    }

    public List<Gender> getAllGenders(String sid) throws Exception {
        try {
            String fields = "[\"*\"]";
            ResponseEntity<Map> response = erpNextApiService.getResource("Gender", fields, null, sid);
            List<Map<String, Object>> genderData = (List<Map<String, Object>>) response.getBody().get("data");
            return convertIntoGender(genderData);
        } catch (Exception e) {
            logger.error("Error fetching genders: {}", e.getMessage());
            throw new Exception("Error fetching genders: " + e.getMessage());
        }
    }
}
