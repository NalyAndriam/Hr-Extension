package com.eval.erp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class ErpNextApiService {

    private static final Logger logger = LoggerFactory.getLogger(ErpNextApiService.class);

    @Value("${erpnext.api.url}")
    private String erpNextApiUrl;

    public ResponseEntity<Map> getResource(String resourceType, String fields, String filters, String sid) {
        String url = erpNextApiUrl + "/api/resource/" + resourceType + "?fields=" + fields;
        if (filters != null && !filters.isEmpty()) {
            url += "&filters=" + filters;
        }
        return executeGetRequest(url, sid);
    }

    public ResponseEntity<Map> updateResource(String resourceType, String id, Map<String, Object> data, String sid) {
        String url = erpNextApiUrl + "/api/resource/" + resourceType + "/" + id;
        return executePutRequest(url, data, sid);
    }

    public ResponseEntity<Map> postResource(String resourceType, Map<String, Object> data, String sid) {
        String url = erpNextApiUrl + "/api/resource/" + resourceType;
        return executePostRequest(url, data, sid);
    }

    private ResponseEntity<Map> executeGetRequest(String url, String sid) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", sid);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            logger.info("GET request to: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, request, Map.class);
            logger.info("Response: StatusCode={}", response.getStatusCode());
            return response;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error: {} - Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            throw e;
        }
    }

    private ResponseEntity<Map> executePostRequest(String url, Map<String, Object> data, String sid) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", sid);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
        try {
            logger.info("POST request to: {} with data: {}", url, data);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
            logger.info("Response: StatusCode={}", response.getStatusCode());
            return response;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error: {} - Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            throw e;
        }
    }

    private ResponseEntity<Map> executePutRequest(String url, Map<String, Object> data, String sid) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", sid);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(data, headers);
        try {
            logger.info("PUT request to: {} with data: {}", url, data);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);
            logger.info("Response: StatusCode={}", response.getStatusCode());
            return response;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP error: {} - Response: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error: {}", e.getMessage());
            throw e;
        }
    }

}