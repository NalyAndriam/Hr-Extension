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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
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

    public ResponseEntity<Map> getResource(String doctype, String fields, String filters, String sid, int limitPageLength, int limitStart) throws Exception {
        String url= erpNextApiUrl; 
        url += String.format("/api/resource/%s?fields=%s&filters=%s&limit_page_length=%d&limit_start=%d", 
                                 doctype, fields, filters, limitPageLength, limitStart);
        return executeGetRequest(url, sid);
    }

    public ResponseEntity<Map> getResource(String doctype, String fields, String filters, String sid, int limitPageLength) throws Exception {
        String url= erpNextApiUrl; 
        url += String.format("/api/resource/%s?fields=%s&filters=%s&limit_page_length=%d", 
                                 doctype, fields, filters, limitPageLength);
        return executeGetRequest(url, sid);
    }

    // New getResourceById method (for direct ID-based fetching)
    public ResponseEntity<Map> getResourceById(String resourcePath, String sid) {
        String url = erpNextApiUrl + "/api/resource/" + resourcePath;
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

    public ResponseEntity<Map> deleteResource(String resourceType, String id, String sid) {
        String url = erpNextApiUrl + "/api/resource/" + resourceType + "/" + id;
        return executeDeleteRequest(url, sid);
    }

    public ResponseEntity<Map> cancelResource(String resourceType, String id, String sid) {
        String url = erpNextApiUrl + "/api/resource/" + resourceType + "/" + id + "?run_method=cancel";
        return executePostRequest(url, null, sid);
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

    private ResponseEntity<Map> executeDeleteRequest(String url, String sid) {
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cookie", sid);
        HttpEntity<Void> request = new HttpEntity<>(headers);
        try {
            logger.info("DELETE request to: {}", url);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.DELETE, request, Map.class);
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

    

    public ResponseEntity<Map> submitDocument(Map<String, Object> data, String sid) {
        String url = erpNextApiUrl + "/api/method/frappe.client.submit";
        return executePostRequest(url, data, sid);
    }

    public ResponseEntity<Map> submitResource(String resourceType, String id, String sid) {
        String url = erpNextApiUrl + "/api/resource/" + resourceType + "/" + id + "?run_method=submit";
        return executePostRequest(url, null, sid);
    }

    public ResponseEntity<Map> runDocumentMethod(String doctype, String docName, String methodName, Map<String, Object> args, String sid) {
        String url = erpNextApiUrl + "/api/method/frappe.client.run_doc_method";
        Map<String, Object> data = new HashMap<>();
        data.put("dt", doctype);
        data.put("dn", docName);
        data.put("method", methodName);
        if (args != null && !args.isEmpty()) {
            data.put("args", args);
        }
        return executePostRequest(url, data, sid);
    }


}