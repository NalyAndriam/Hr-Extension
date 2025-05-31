package com.eval.erp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.List;

@Component
public class ErpNextAuthenticationProvider implements AuthenticationProvider {

    @Value("${erpnext.api.url}")
    private String erpNextApiUrl;

    private final HttpSession session;

    public ErpNextAuthenticationProvider(HttpSession session) {
        this.session = session;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        String erpUrl = erpNextApiUrl + "/api/method/login";
        System.out.println("Appel à l'API ERPNext : " + erpUrl + " avec username : " + username);

        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("usr", username);
        formData.add("pwd", password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(formData, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                erpUrl, HttpMethod.POST, request, String.class
            );
            System.out.println("Réponse ERPNext : " + response.getBody());
            System.out.println("En-têtes : " + response.getHeaders());

            List<String> cookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            System.out.println("Cookies reçus : " + cookies);

            String sid = cookies != null ? cookies.stream()
                .filter(c -> c.startsWith("sid="))
                .map(c -> c.split(";")[0]) // Extraire uniquement la partie sid=xxx
                .findFirst().orElse(null) : null;

            if (sid == null) {
                System.err.println("Aucun cookie SID reçu");
                throw new BadCredentialsException("Pas de session ERPNext reçue");
            }

            System.out.println("SID stocké dans la session : " + sid);
            session.setAttribute("erp_sid", sid);

            // Retourner un token d'authentification sans rôles pour l'instant
            return new UsernamePasswordAuthenticationToken(
                username, null, Collections.emptyList()
            );

        } catch (HttpClientErrorException e) {
            System.err.println("Erreur HTTP : " + e.getStatusCode());
            System.err.println("Réponse : " + e.getResponseBodyAsString());
            throw new BadCredentialsException("Identifiants invalides");
        } catch (Exception e) {
            System.err.println("Erreur inattendue lors de l'authentification : " + e.getMessage());
            e.printStackTrace();
            throw new AuthenticationException("Erreur d'authentification : " + e.getMessage(), e) {};
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}